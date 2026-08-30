package com.minhnb.finvera_be.portfolio.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.FifoLot;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.PortfolioHoldingsState;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.PositionResult;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.TransactionInput;
import com.minhnb.finvera_be.portfolio.domain.model.PortfolioTypes.TransactionType;
import com.minhnb.finvera_be.portfolio.dto.PortfolioSummaryResponse;
import com.minhnb.finvera_be.portfolio.dto.PositionResponse;
import com.minhnb.finvera_be.portfolio.dto.PositionsResponse;
import com.minhnb.finvera_be.portfolio.entity.PortfolioEntity;
import com.minhnb.finvera_be.portfolio.entity.PortfolioTransactionEntity;
import com.minhnb.finvera_be.portfolio.repository.PortfolioRepository;
import com.minhnb.finvera_be.portfolio.repository.PortfolioTransactionRepository;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PortfolioNotFoundException;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService.DailyBarReference;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.stock.domain.time.StockFreshnessPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PositionService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioTransactionRepository transactionRepository;
    private final MarketReferenceDataService marketReferenceData;
    private final StockReferenceDataService stockReferenceData;
    private final OwnerScopedAccess ownerScopedAccess;
    private final Clock clock;
    private final StockFreshnessPolicy freshnessPolicy = new StockFreshnessPolicy();

    public PositionService(
            PortfolioRepository portfolioRepository,
            PortfolioTransactionRepository transactionRepository,
            MarketReferenceDataService marketReferenceData,
            StockReferenceDataService stockReferenceData,
            OwnerScopedAccess ownerScopedAccess,
            Clock clock) {
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.marketReferenceData = marketReferenceData;
        this.stockReferenceData = stockReferenceData;
        this.ownerScopedAccess = ownerScopedAccess;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PositionsResponse getPositions(UUID portfolioId) {
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();
        portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNull(portfolioId, ownerId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));

        Holdings holdings = computeHoldings(portfolioId);
        PortfolioHoldingsState state = holdings.state();
        List<PortfolioTransactionEntity> rawTxs = transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(portfolioId);

        // Build list of position responses
        List<PositionResponse> positionResponses = new ArrayList<>();
        List<String> coherenceParts = new ArrayList<>();

        for (PortfolioTransactionEntity tx : rawTxs) {
            coherenceParts.add(tx.getId().toString());
        }

        for (PositionResult pos : state.positions().values()) {
            if (pos.quantity().signum() > 0) {
                String priceStatus = pos.priceAvailable() ? "DEFINED" : "MISSING";
                PriceFreshness freshness = holdings.freshnessByInstrument()
                        .getOrDefault(pos.instrumentId(), PriceFreshness.UNAVAILABLE);
                positionResponses.add(new PositionResponse(
                        pos.symbol() != null ? pos.symbol() : "UNKNOWN",
                        formatDecimal(pos.quantity()),
                        pos.averageCostBasis() != null ? formatDecimal(pos.averageCostBasis()) : "0",
                        pos.currentPrice() != null ? formatDecimal(pos.currentPrice()) : null,
                        priceStatus,
                        freshness.status().name(),
                        freshness.tradingDate(),
                        pos.unrealizedPL() != null ? formatDecimal(pos.unrealizedPL()) : null,
                        pos.realizedPL() != null ? formatDecimal(pos.realizedPL()) : "0",
                        pos.allocation() != null ? formatDecimal(pos.allocation()) : null));
            }
        }

        String coherenceKey = PortfolioCoherenceKey.of(coherenceParts);
        Instant asOf = Instant.now(clock);
        PortfolioStatus status = portfolioStatus(state, holdings.freshnessByInstrument());

        return new PositionsResponse(
                positionResponses,
                formatDecimal(state.totals().cashBalance()),
                formatDecimal(state.totals().totalValue()),
                status.dataStatus().name(),
                status.reasonCodes(),
                coherenceKey,
                asOf);
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse calculatePortfolioTotals(UUID portfolioId, String portfolioName, Instant createdAt) {
        Holdings holdings = computeHoldings(portfolioId);
        PortfolioHoldingsState state = holdings.state();
        Instant asOf = Instant.now(clock);
        PortfolioStatus status = portfolioStatus(state, holdings.freshnessByInstrument());

        return new PortfolioSummaryResponse(
                portfolioId,
                portfolioName,
                createdAt,
                formatDecimal(state.totals().totalValue()),
                formatDecimal(state.totals().cashBalance()),
                formatDecimal(state.totals().totalUnrealizedPL()),
                formatDecimal(state.totals().totalRealizedPL()),
                status.dataStatus().name(),
                status.reasonCodes(),
                asOf);
    }

    @Transactional(readOnly = true)
    public List<PortfolioSummaryResponse> calculatePortfolioSummaries(List<PortfolioEntity> portfolios) {
        if (portfolios == null || portfolios.isEmpty()) {
            return List.of();
        }

        List<UUID> portfolioIds = portfolios.stream().map(PortfolioEntity::getId).toList();
        List<PortfolioTransactionEntity> allTxs =
                transactionRepository.findByPortfolioIdInOrderByExecutedAtAscSequenceNoAsc(portfolioIds);

        Map<UUID, List<PortfolioTransactionEntity>> txsByPortfolio = allTxs.stream()
                .collect(Collectors.groupingBy(PortfolioTransactionEntity::getPortfolioId));

        Set<UUID> allInstrumentIds = allTxs.stream()
                .map(PortfolioTransactionEntity::getInstrumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Instant asOf = Instant.now(clock);
        PricedUniverse universe = priceUniverse(allInstrumentIds, asOf);

        return portfolios.stream().map(p -> {
            List<PortfolioTransactionEntity> rawTxs = txsByPortfolio.getOrDefault(p.getId(), List.of());
            List<TransactionInput> inputs = rawTxs.stream()
                    .map(tx -> toTransactionInput(tx, universe.symbols().get(tx.getInstrumentId())))
                    .toList();

            PortfolioHoldingsState state = PortfolioAnalyticsV1.replayHoldings(
                    inputs, universe.currentPrices(), universe.symbols());
            PortfolioStatus status = portfolioStatus(state, universe.freshness());

            return new PortfolioSummaryResponse(
                    p.getId(),
                    p.getName(),
                    p.getCreatedAt(),
                    formatDecimal(state.totals().totalValue()),
                    formatDecimal(state.totals().cashBalance()),
                    formatDecimal(state.totals().totalUnrealizedPL()),
                    formatDecimal(state.totals().totalRealizedPL()),
                    status.dataStatus().name(),
                    status.reasonCodes(),
                    asOf);
        }).toList();
    }

    public static String formatDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    @Transactional(readOnly = true)
    public PortfolioHoldingsState computeHoldingsState(UUID portfolioId) {
        return computeHoldings(portfolioId).state();
    }

    @Transactional(readOnly = true)
    public Holdings computeHoldings(UUID portfolioId) {
        List<PortfolioTransactionEntity> rawTxs = transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(portfolioId);

        Set<UUID> instrumentIds = rawTxs.stream()
                .map(PortfolioTransactionEntity::getInstrumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        PricedUniverse universe = priceUniverse(instrumentIds, Instant.now(clock));

        List<TransactionInput> inputs = rawTxs.stream()
                .map(tx -> toTransactionInput(tx, universe.symbols().get(tx.getInstrumentId())))
                .toList();

        return new Holdings(
                PortfolioAnalyticsV1.replayHoldings(inputs, universe.currentPrices(), universe.symbols()),
                universe.freshness());
    }

    /**
     * Resolves symbol, latest accepted close, and (contract U-8) the freshness of
     * that close for every instrument, using the same
     * {@link StockFreshnessPolicy#evaluateDailyBarSeries} rule the stock module
     * applies (0 sessions behind = CURRENT, 1 = DELAYED, more = STALE), measured
     * against the instrument venue's current market session.
     */
    private PricedUniverse priceUniverse(Set<UUID> instrumentIds, Instant asOf) {
        Map<UUID, String> symbols = new HashMap<>();
        Map<UUID, String> venues = new HashMap<>();
        Map<UUID, BigDecimal> currentPrices = new HashMap<>();
        Map<UUID, PriceFreshness> freshness = new HashMap<>();
        if (instrumentIds.isEmpty()) {
            return new PricedUniverse(symbols, currentPrices, freshness);
        }
        for (InstrumentReference inst : marketReferenceData.findInstrumentsByIds(instrumentIds)) {
            symbols.put(inst.instrumentId(), inst.symbol());
            venues.put(inst.instrumentId(), inst.venue());
        }
        Map<String, LocalDate> sessionDateByVenue = new HashMap<>();
        for (DailyBarReference bar : stockReferenceData.findLatestDailyBars(instrumentIds)) {
            if (bar.closePrice() == null) {
                continue;
            }
            currentPrices.put(bar.instrumentId(), bar.closePrice());
            String venue = venues.get(bar.instrumentId());
            if (venue == null) {
                freshness.put(bar.instrumentId(), new PriceFreshness(DataStatus.UNAVAILABLE, bar.tradingDate()));
                continue;
            }
            LocalDate sessionDate = sessionDateByVenue.computeIfAbsent(venue,
                    v -> marketReferenceData.resolveSession(v, asOf).tradingDate());
            int sessionsBehind = countWeekdaysBetween(bar.tradingDate(), sessionDate);
            freshness.put(bar.instrumentId(),
                    new PriceFreshness(freshnessPolicy.evaluateDailyBarSeries(sessionsBehind), bar.tradingDate()));
        }
        return new PricedUniverse(symbols, currentPrices, freshness);
    }

    /**
     * Contract U-8: an unpriced open position makes the whole valuation PARTIAL
     * (its total is a lower bound); otherwise the portfolio inherits the most
     * actionable freshness among its open positions' prices.
     */
    static PortfolioStatus portfolioStatus(PortfolioHoldingsState state, Map<UUID, PriceFreshness> freshness) {
        DataStatus worst = DataStatus.CURRENT;
        List<String> reasons = new ArrayList<>();
        boolean anyUnpriced = false;
        for (PositionResult pos : state.positions().values()) {
            if (pos.quantity().signum() <= 0) {
                continue;
            }
            if (!pos.priceAvailable()) {
                anyUnpriced = true;
                continue;
            }
            DataStatus status = freshness.getOrDefault(pos.instrumentId(), PriceFreshness.UNAVAILABLE).status();
            worst = DataStatus.mostActionable(worst, status);
        }
        if (worst == DataStatus.DELAYED) {
            reasons.add("POSITION_PRICE_DELAYED");
        } else if (worst == DataStatus.STALE) {
            reasons.add("POSITION_PRICE_STALE");
        }
        if (anyUnpriced) {
            reasons.add("POSITION_PRICE_UNAVAILABLE");
            return new PortfolioStatus(DataStatus.PARTIAL, List.copyOf(reasons));
        }
        return new PortfolioStatus(worst == DataStatus.UNAVAILABLE ? DataStatus.PARTIAL : worst, List.copyOf(reasons));
    }

    /** Same weekday-count approximation as {@code StockOverviewService}; see its Javadoc for the caveat. */
    private static int countWeekdaysBetween(LocalDate lastAccepted, LocalDate asOfTradingDate) {
        int count = 0;
        LocalDate cursor = lastAccepted;
        while (cursor.isBefore(asOfTradingDate)) {
            cursor = cursor.plusDays(1);
            if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }

    public record PriceFreshness(DataStatus status, LocalDate tradingDate) {
        static final PriceFreshness UNAVAILABLE = new PriceFreshness(DataStatus.UNAVAILABLE, null);
    }

    public record PortfolioStatus(DataStatus dataStatus, List<String> reasonCodes) {
    }

    public record Holdings(PortfolioHoldingsState state, Map<UUID, PriceFreshness> freshnessByInstrument) {
    }

    private record PricedUniverse(Map<UUID, String> symbols, Map<UUID, BigDecimal> currentPrices,
                                  Map<UUID, PriceFreshness> freshness) {
    }

    public static TransactionInput toTransactionInput(PortfolioTransactionEntity tx, String symbol) {
        return new TransactionInput(
                tx.getId(),
                tx.getSequenceNo() != null ? tx.getSequenceNo() : 0L,
                TransactionType.valueOf(tx.getTransactionType()),
                tx.getInstrumentId(),
                symbol,
                tx.getQuantity(),
                tx.getPrice(),
                tx.getFee(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getExecutedAt(),
                tx.getEntryAt(),
                tx.getVoidsTransactionId(),
                tx.getVoidReason());
    }
}
