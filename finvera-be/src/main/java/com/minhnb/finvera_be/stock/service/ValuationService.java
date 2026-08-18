package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalSummaryCalculator;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.ValuationLabel;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.AssessmentResult;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.HistoryPoint;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.Inputs;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.MetricResult;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.SectorPoint;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.FundamentalReportEntity;
import com.minhnb.finvera_be.stock.entity.FundamentalReportMetricEntity;
import com.minhnb.finvera_be.stock.entity.SectorReferenceEntity;
import com.minhnb.finvera_be.stock.entity.ValuationAssessmentEntity;
import com.minhnb.finvera_be.stock.entity.ValuationAssessmentInputEntity;
import com.minhnb.finvera_be.stock.entity.ValuationMetricEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalReportMetricRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalReportRepository;
import com.minhnb.finvera_be.stock.repository.SectorReferenceRepository;
import com.minhnb.finvera_be.stock.repository.ValuationAssessmentInputRepository;
import com.minhnb.finvera_be.stock.repository.ValuationAssessmentRepository;
import com.minhnb.finvera_be.stock.repository.ValuationMetricRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-008, FR-009, FR-010, FR-014, FR-015; DATA-009, DATA-010; NFR-003.
 * Service calculating and caching the valuation-v1 assessment read model.
 */
@Service
public class ValuationService {

    private final MarketReferenceDataService referenceData;
    private final EquityDailyBarRepository dailyBars;
    private final EquityProfileRepository profiles;
    private final FundamentalReportRepository reports;
    private final FundamentalReportMetricRepository reportMetrics;
    private final SectorReferenceRepository sectors;
    private final ValuationAssessmentRepository assessments;
    private final ValuationMetricRepository valuationMetrics;
    private final ValuationAssessmentInputRepository assessmentInputs;
    private final FundamentalReportService fundamentalReportService;
    private final ValuationV1 engine = new ValuationV1();
    private final Clock clock;

    public ValuationService(
            MarketReferenceDataService referenceData,
            EquityDailyBarRepository dailyBars,
            EquityProfileRepository profiles,
            FundamentalReportRepository reports,
            FundamentalReportMetricRepository reportMetrics,
            SectorReferenceRepository sectors,
            ValuationAssessmentRepository assessments,
            ValuationMetricRepository valuationMetrics,
            ValuationAssessmentInputRepository assessmentInputs,
            FundamentalReportService fundamentalReportService,
            Clock clock) {
        this.referenceData = referenceData;
        this.dailyBars = dailyBars;
        this.profiles = profiles;
        this.reports = reports;
        this.reportMetrics = reportMetrics;
        this.sectors = sectors;
        this.assessments = assessments;
        this.valuationMetrics = valuationMetrics;
        this.assessmentInputs = assessmentInputs;
        this.fundamentalReportService = fundamentalReportService;
        this.clock = clock;
    }

    @Transactional
    public Optional<StockValuation> findBySymbol(String symbol) {
        Optional<InstrumentReference> instrumentOpt = referenceData.findActiveInstrumentBySymbol(symbol);
        if (instrumentOpt.isEmpty()) {
            return Optional.empty();
        }

        UUID instrumentId = instrumentOpt.get().instrumentId();
        Instant asOf = clock.instant();
        LocalDate asOfDate = LocalDate.now(clock);

        // Fetch current price from latest daily bar
        List<EquityDailyBarEntity> ascendingBars = dailyBars.findByInstrumentIdAndCurrentTrueOrderByTradingDateAsc(instrumentId);
        EquityDailyBarEntity latestBar = ascendingBars.isEmpty() ? null : ascendingBars.get(ascendingBars.size() - 1);
        BigDecimal currentPrice = latestBar != null ? latestBar.getClosePrice() : null;

        // Fetch equity profile
        Optional<EquityProfileEntity> profileOpt = profiles.findFirstByInstrumentIdAndEffectiveToIsNull(instrumentId);
        Long sharesOutstanding = profileOpt.map(EquityProfileEntity::getSharesOutstanding).orElse(null);

        // Fetch fundamentals
        var fundamentalsOpt = fundamentalReportService.findBySymbol(symbol);
        BigDecimal epsTtm = null;
        BigDecimal epsGrowth = null;
        BigDecimal equityParent = null;
        BigDecimal ebitdaTtm = null;
        BigDecimal totalDebt = null;
        BigDecimal cash = null;
        BigDecimal dividendTtm = null;
        String fundamentalsStatus = "UNAVAILABLE";

        if (fundamentalsOpt.isPresent()) {
            var f = fundamentalsOpt.get();
            fundamentalsStatus = f.dataStatus().name();
            for (var m : f.metrics()) {
                if (m.applicability() == MetricApplicability.DEFINED && m.value() != null) {
                    switch (m.metricCode()) {
                        case "EPS_TTM" -> epsTtm = m.value();
                        case "EPS_GROWTH_PERCENT" -> epsGrowth = m.value();
                        case "EQUITY_ATTRIBUTABLE_TO_PARENT" -> equityParent = m.value();
                        case "EBITDA_TTM" -> ebitdaTtm = m.value();
                        case "TOTAL_DEBT" -> totalDebt = m.value();
                        case "CASH_AND_EQUIVALENTS" -> cash = m.value();
                        case "DIVIDEND_PER_SHARE_TTM" -> dividendTtm = m.value();
                    }
                }
            }
        }

        // Build history series from recent daily bars (up to 750)
        List<HistoryPoint> historyPoints = new ArrayList<>();
        if (epsTtm != null && epsTtm.compareTo(BigDecimal.ZERO) > 0) {
            for (int i = ascendingBars.size() - 1; i >= 0 && historyPoints.size() < 750; i--) {
                EquityDailyBarEntity bar = ascendingBars.get(i);
                if (bar.getClosePrice() != null) {
                    BigDecimal pe = bar.getClosePrice().divide(epsTtm, 12, java.math.RoundingMode.HALF_UP);
                    historyPoints.add(new HistoryPoint("PE", pe));
                }
            }
        }

        SectorReferenceEntity sectorRef = profileOpt.flatMap(p ->
                p.getSectorReferenceId() != null ? sectors.findById(p.getSectorReferenceId()) : Optional.empty()
        ).orElse(null);

        Inputs inputs = Inputs.builder()
                .price(currentPrice)
                .sharesOutstanding(sharesOutstanding)
                .epsTtm(epsTtm)
                .epsGrowthPercent(epsGrowth)
                .equityAttributableToParent(equityParent)
                .ebitdaTtm(ebitdaTtm)
                .totalDebt(totalDebt)
                .cashAndEquivalents(cash)
                .dividendPerShareTtm(dividendTtm)
                .ownHistorySeries(historyPoints)
                .sectorSeries(List.of()) // Fixture mode: sector basis default disabled unless fixture provides
                .priceDataStatus(latestBar != null ? "CURRENT" : "UNAVAILABLE")
                .fundamentalsDataStatus(fundamentalsStatus)
                .build();

        AssessmentResult result = engine.classify(inputs);

        List<String> coherenceParts = new ArrayList<>();
        coherenceParts.add("VALUATION");
        coherenceParts.add(symbol);
        if (latestBar != null) {
            coherenceParts.add(latestBar.getId().toString());
            coherenceParts.add(String.valueOf(latestBar.getRevision()));
        }
        fundamentalsOpt.ifPresent(f -> coherenceParts.add(f.coherenceKey()));

        String coherenceKey = CoherenceKeys.of(coherenceParts);

        List<ValuationMetric> responseMetrics = result.metrics().stream()
                .map(m -> new ValuationMetric(
                        m.metricCode(),
                        m.value(),
                        m.applicability(),
                        m.ownHistoryPercentile(),
                        m.sectorPercentile(),
                        m.effectiveWeight(),
                        m.reasonCode()
                ))
                .toList();

        DataStatus dataStatus = result.published() ? DataStatus.CURRENT : DataStatus.UNAVAILABLE;

        return Optional.of(new StockValuation(
                symbol,
                ValuationV1.RULE_VERSION,
                result.published(),
                result.classification(),
                result.score(),
                result.displayedScore(),
                result.confidence(),
                result.usedOwnHistory(),
                result.usedSector(),
                sectorRef != null ? sectorRef.getSectorCode() : null,
                sectorRef != null ? sectorRef.getScheme() : null,
                sectorRef != null ? sectorRef.getSchemeVersion() : null,
                result.sectorConstituentCount() > 0 ? result.sectorConstituentCount() : null,
                result.historyPointCount() > 0 ? result.historyPointCount() : null,
                responseMetrics,
                dataStatus,
                result.reasonCodes(),
                asOfDate,
                asOf,
                coherenceKey
        ));
    }

    public record StockValuation(
            String symbol,
            String ruleVersion,
            boolean published,
            ValuationLabel classification,
            BigDecimal score,
            Integer displayedScore,
            Integer confidence,
            boolean usedOwnHistory,
            boolean usedSector,
            String sector,
            String sectorScheme,
            String sectorSchemeVersion,
            Integer sectorConstituentCount,
            Integer historyPointCount,
            List<ValuationMetric> metrics,
            DataStatus dataStatus,
            List<String> reasonCodes,
            LocalDate tradingDate,
            Instant asOf,
            String coherenceKey
    ) {}

    public record ValuationMetric(
            String metricCode,
            BigDecimal value,
            MetricApplicability applicability,
            BigDecimal ownHistoryPercentile,
            BigDecimal sectorPercentile,
            BigDecimal effectiveWeight,
            String reasonCode
    ) {}
}
