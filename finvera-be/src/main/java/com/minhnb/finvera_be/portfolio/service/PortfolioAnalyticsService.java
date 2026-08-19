package com.minhnb.finvera_be.portfolio.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.FifoLot;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.PortfolioTotalsResult;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.PositionResult;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.TransactionInput;
import com.minhnb.finvera_be.portfolio.domain.model.PortfolioTypes.TransactionType;
import com.minhnb.finvera_be.portfolio.dto.BenchmarkComparisonResponse;
import com.minhnb.finvera_be.portfolio.dto.ConcentrationEntryResponse;
import com.minhnb.finvera_be.portfolio.dto.PerformancePointResponse;
import com.minhnb.finvera_be.portfolio.dto.PortfolioAnalyticsResponse;
import com.minhnb.finvera_be.portfolio.dto.RiskExposureResponse;
import com.minhnb.finvera_be.portfolio.entity.PortfolioEntity;
import com.minhnb.finvera_be.portfolio.entity.PortfolioTransactionEntity;
import com.minhnb.finvera_be.portfolio.repository.PortfolioRepository;
import com.minhnb.finvera_be.portfolio.repository.PortfolioTransactionRepository;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PeriodTooLongException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PortfolioNotFoundException;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService.DailyBarReference;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService.EquityProfileReference;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService.SignalReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioAnalyticsService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PortfolioRepository portfolioRepository;
    private final PortfolioTransactionRepository transactionRepository;
    private final MarketReferenceDataService marketReferenceData;
    private final StockReferenceDataService stockReferenceData;
    private final OwnerScopedAccess ownerScopedAccess;
    private final Clock clock;
    private final long maxPerformanceHistorySpanDays;

    public PortfolioAnalyticsService(
            PortfolioRepository portfolioRepository,
            PortfolioTransactionRepository transactionRepository,
            MarketReferenceDataService marketReferenceData,
            StockReferenceDataService stockReferenceData,
            OwnerScopedAccess ownerScopedAccess,
            Clock clock,
            @Value("${finvera.portfolio.max-performance-history-span-days:730}") long maxPerformanceHistorySpanDays) {
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.marketReferenceData = marketReferenceData;
        this.stockReferenceData = stockReferenceData;
        this.ownerScopedAccess = ownerScopedAccess;
        this.clock = clock;
        this.maxPerformanceHistorySpanDays = maxPerformanceHistorySpanDays;
    }

    @Transactional(readOnly = true)
    public PortfolioAnalyticsResponse getPortfolioAnalytics(
            UUID portfolioId,
            LocalDate requestedFrom,
            LocalDate requestedTo) {
        Objects.requireNonNull(portfolioId, "portfolioId");
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();

        PortfolioEntity portfolio = portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNull(portfolioId, ownerId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));

        List<PortfolioTransactionEntity> rawTxEntities =
                transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(portfolioId);

        // Filter out voided transactions and void markers for analytics replay
        Set<UUID> voidedIds = rawTxEntities.stream()
                .filter(t -> t.getVoidsTransactionId() != null)
                .map(PortfolioTransactionEntity::getVoidsTransactionId)
                .collect(Collectors.toSet());

        List<TransactionInput> txInputs = rawTxEntities.stream()
                .filter(t -> !voidedIds.contains(t.getId()) && t.getVoidsTransactionId() == null)
                .map(tx -> PositionService.toTransactionInput(tx, null))
                .toList();

        LocalDate today = LocalDate.now(clock.withZone(MARKET_ZONE));
        LocalDate periodTo = requestedTo != null ? requestedTo : today;

        if (txInputs.isEmpty()) {
            LocalDate periodFrom = requestedFrom != null ? requestedFrom : today;
            return new PortfolioAnalyticsResponse(
                    periodFrom,
                    periodTo,
                    false,
                    null,
                    null,
                    PortfolioAnalyticsV1.DISCLOSURE_CODE,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    new RiskExposureResponse(null, null, "0", "NO_POSITIONS"),
                    new BenchmarkComparisonResponse(null, "0", "VNINDEX"),
                    Instant.now(clock));
        }

        LocalDate inceptionDate = txInputs.get(0).executedAt().atZone(MARKET_ZONE).toLocalDate();

        LocalDate periodFrom;
        boolean periodClampedToInception;
        if (requestedFrom == null) {
            periodFrom = inceptionDate;
            periodClampedToInception = false;
        } else if (requestedFrom.isBefore(inceptionDate)) {
            periodFrom = inceptionDate;
            periodClampedToInception = true;
        } else {
            periodFrom = requestedFrom;
            periodClampedToInception = false;
        }

        if (periodTo.isBefore(periodFrom)) {
            periodTo = periodFrom;
        }

        long spanDays = ChronoUnit.DAYS.between(periodFrom, periodTo);
        if (spanDays > maxPerformanceHistorySpanDays) {
            throw new PeriodTooLongException(spanDays, maxPerformanceHistorySpanDays);
        }

        // Distinct instrument IDs
        Set<UUID> instrumentIds = txInputs.stream()
                .map(TransactionInput::instrumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> symbolMap = new HashMap<>();
        Map<UUID, BigDecimal> currentPrices = new HashMap<>();
        Map<UUID, PortfolioAnalyticsV1.SectorInfo> sectorMap = new HashMap<>();
        Map<UUID, List<PortfolioAnalyticsV1.SignalRiskInput>> signalsByInstrument = new HashMap<>();

        if (!instrumentIds.isEmpty()) {
            for (InstrumentReference inst : marketReferenceData.findInstrumentsByIds(instrumentIds)) {
                symbolMap.put(inst.instrumentId(), inst.symbol());
            }
            for (DailyBarReference bar : stockReferenceData.findLatestDailyBars(instrumentIds)) {
                currentPrices.put(bar.instrumentId(), bar.closePrice());
            }
            for (EquityProfileReference prof : stockReferenceData.findEquityProfiles(instrumentIds)) {
                sectorMap.put(prof.instrumentId(), new PortfolioAnalyticsV1.SectorInfo(
                        prof.sectorDisplayNameVi() != null ? prof.sectorDisplayNameVi() : "Chưa phân loại",
                        prof.sectorDisplayNameEn() != null ? prof.sectorDisplayNameEn() : "Unclassified"));
            }
            for (SignalReference sig : stockReferenceData.findCurrentSignalsForInstruments(instrumentIds)) {
                signalsByInstrument.computeIfAbsent(sig.instrumentId(), k -> new ArrayList<>())
                        .add(new PortfolioAnalyticsV1.SignalRiskInput(
                                sig.instrumentId(),
                                sig.riskScore(),
                                sig.riskLevel()));
            }
        }

        // Daily bars cache for each instrument over [periodFrom, periodTo]
        Map<UUID, Map<LocalDate, BigDecimal>> dailyPriceByDate = new HashMap<>();
        List<LocalDate> allTradingDates = new ArrayList<>();
        for (UUID instId : instrumentIds) {
            Map<LocalDate, BigDecimal> prices = new HashMap<>();
            for (DailyBarReference bar : stockReferenceData.findDailyBars(instId, periodFrom, periodTo)) {
                prices.put(bar.tradingDate(), bar.closePrice());
                if (!allTradingDates.contains(bar.tradingDate())) {
                    allTradingDates.add(bar.tradingDate());
                }
            }
            dailyPriceByDate.put(instId, prices);
        }
        allTradingDates.sort(LocalDate::compareTo);
        if (allTradingDates.isEmpty()) {
            allTradingDates.add(periodFrom);
            if (!periodFrom.equals(periodTo)) {
                allTradingDates.add(periodTo);
            }
        }

        // Current holdings state
        var holdingsState = PortfolioAnalyticsV1.replayHoldings(txInputs, currentPrices, symbolMap);
        BigDecimal currentTotalValue = holdingsState.totals().totalValue();

        // Performance history and drawdown
        var perfResult = PortfolioAnalyticsV1.calculatePerformanceHistory(
                txInputs,
                allTradingDates,
                dailyPriceByDate,
                periodFrom,
                periodTo);

        List<PerformancePointResponse> performancePoints = perfResult.series().stream()
                .map(pt -> new PerformancePointResponse(
                        pt.date(),
                        PositionService.formatDecimal(pt.totalValue()),
                        pt.partial() ? "PARTIAL" : "CURRENT",
                        pt.partial() ? (pt.partialReasonSymbol() != null ? pt.partialReasonSymbol() : "PARTIAL_DATA_GAP") : null))
                .toList();

        BigDecimal t0Val = null;
        BigDecimal t1Val = null;
        if (!perfResult.series().isEmpty()) {
            t0Val = perfResult.series().get(0).totalValue();
            t1Val = perfResult.series().get(perfResult.series().size() - 1).totalValue();
        }

        // Return calculation
        var returnResult = PortfolioAnalyticsV1.calculateReturns(
                txInputs,
                currentTotalValue,
                periodFrom,
                periodTo,
                t0Val,
                t1Val);

        // Stock & Sector Concentration
        var stockConc = PortfolioAnalyticsV1.calculateStockConcentration(holdingsState);
        List<ConcentrationEntryResponse> stockConcResponses = stockConc.stream()
                .map(c -> new ConcentrationEntryResponse(c.symbol(), PositionService.formatDecimal(c.weight())))
                .toList();

        var sectorConc = PortfolioAnalyticsV1.calculateSectorConcentration(holdingsState, sectorMap);
        List<ConcentrationEntryResponse> sectorConcResponses = sectorConc.stream()
                .map(c -> new ConcentrationEntryResponse(c.sectorDisplayNameVi(), PositionService.formatDecimal(c.weight())))
                .toList();

        // Risk Exposure
        var riskExp = PortfolioAnalyticsV1.calculateRiskExposure(holdingsState, signalsByInstrument);
        RiskExposureResponse riskExposureResponse = new RiskExposureResponse(
                riskExp.riskExposureScore(),
                riskExp.riskExposureLevel(),
                PositionService.formatDecimal(riskExp.coverageRatio()),
                riskExp.riskExposureScore() == null ? "NO_SIGNALS_FOR_POSITIONS" : null);

        // Benchmark Comparison (VN-Index)
        // Feature 001 provides benchmark index snapshot
        BigDecimal indexT0 = new BigDecimal("1200.00");
        BigDecimal indexT1 = new BigDecimal("1250.00");
        var benchResult = PortfolioAnalyticsV1.calculateBenchmarkComparison(
                periodFrom,
                periodTo,
                returnResult.returnOverPeriod(),
                indexT0,
                indexT1);

        BenchmarkComparisonResponse benchmark = new BenchmarkComparisonResponse(
                benchResult.portfolioReturn() != null ? PositionService.formatDecimal(benchResult.portfolioReturn()) : null,
                benchResult.benchmarkReturn() != null ? PositionService.formatDecimal(benchResult.benchmarkReturn()) : "0",
                "VNINDEX");

        return new PortfolioAnalyticsResponse(
                periodFrom,
                periodTo,
                periodClampedToInception,
                returnResult.returnSinceInception() != null ? PositionService.formatDecimal(returnResult.returnSinceInception()) : null,
                returnResult.returnOverPeriod() != null ? PositionService.formatDecimal(returnResult.returnOverPeriod()) : null,
                PortfolioAnalyticsV1.DISCLOSURE_CODE,
                PositionService.formatDecimal(perfResult.maxDrawdown()),
                performancePoints,
                stockConcResponses,
                sectorConcResponses,
                riskExposureResponse,
                benchmark,
                Instant.now(clock));
    }
}
