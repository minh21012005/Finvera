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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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

        PortfolioHoldingsState state = computeHoldingsState(portfolioId);
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
                positionResponses.add(new PositionResponse(
                        pos.symbol() != null ? pos.symbol() : "UNKNOWN",
                        formatDecimal(pos.quantity()),
                        pos.averageCostBasis() != null ? formatDecimal(pos.averageCostBasis()) : "0",
                        pos.currentPrice() != null ? formatDecimal(pos.currentPrice()) : null,
                        priceStatus,
                        pos.unrealizedPL() != null ? formatDecimal(pos.unrealizedPL()) : null,
                        pos.realizedPL() != null ? formatDecimal(pos.realizedPL()) : "0",
                        pos.allocation() != null ? formatDecimal(pos.allocation()) : null));
            }
        }

        String coherenceKey = PortfolioCoherenceKey.of(coherenceParts);
        Instant asOf = Instant.now(clock);

        return new PositionsResponse(
                positionResponses,
                formatDecimal(state.totals().cashBalance()),
                formatDecimal(state.totals().totalValue()),
                coherenceKey,
                asOf);
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse calculatePortfolioTotals(UUID portfolioId, String portfolioName, Instant createdAt) {
        PortfolioHoldingsState state = computeHoldingsState(portfolioId);
        Instant asOf = Instant.now(clock);

        return new PortfolioSummaryResponse(
                portfolioId,
                portfolioName,
                createdAt,
                formatDecimal(state.totals().totalValue()),
                formatDecimal(state.totals().cashBalance()),
                formatDecimal(state.totals().totalUnrealizedPL()),
                formatDecimal(state.totals().totalRealizedPL()),
                asOf);
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
        List<PortfolioTransactionEntity> rawTxs = transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(portfolioId);

        Set<UUID> instrumentIds = rawTxs.stream()
                .map(PortfolioTransactionEntity::getInstrumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> symbols = new HashMap<>();
        Map<UUID, BigDecimal> currentPrices = new HashMap<>();

        if (!instrumentIds.isEmpty()) {
            List<InstrumentReference> instruments = marketReferenceData.findInstrumentsByIds(instrumentIds);
            for (InstrumentReference inst : instruments) {
                symbols.put(inst.instrumentId(), inst.symbol());
            }

            List<DailyBarReference> latestBars = stockReferenceData.findLatestDailyBars(instrumentIds);
            for (DailyBarReference bar : latestBars) {
                if (bar.closePrice() != null) {
                    currentPrices.put(bar.instrumentId(), bar.closePrice());
                }
            }
        }

        List<TransactionInput> inputs = rawTxs.stream()
                .map(tx -> toTransactionInput(tx, symbols.get(tx.getInstrumentId())))
                .toList();

        return PortfolioAnalyticsV1.replayHoldings(inputs, currentPrices, symbols);
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
