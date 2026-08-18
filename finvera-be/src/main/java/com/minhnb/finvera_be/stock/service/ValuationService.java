package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalSummaryCalculator;
import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalSummaryCalculator.ReportPeriod;
import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalSummaryCalculator.SummaryResult;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.ValuationLabel;
import com.minhnb.finvera_be.stock.domain.time.StockFreshnessPolicy;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.AssessmentResult;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.ComputedMetrics;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.HistoryPoint;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.Inputs;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.MetricValue;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-008, FR-009, FR-010, FR-014, FR-015; DATA-009, DATA-010; NFR-003.
 * Recomputes the {@code valuation-v1} assessment on every read from accepted
 * facts and persists each instrument/as-of-date outcome as an immutable,
 * revision-chained {@code valuation_assessment} row — mirroring
 * {@code TechnicalIndicatorService}'s pattern — writing a new current row
 * only when the linked inputs (price bar, fundamental summary, own-history
 * fingerprint) actually differ from what is already stored for that date.
 */
@Service
public class ValuationService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_HISTORY_BARS = 750;

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
    private final StockIngestionService ingestion;
    private final ValuationV1 engine = new ValuationV1();
    private final FundamentalSummaryCalculator historyCalculator = new FundamentalSummaryCalculator();
    private final StockFreshnessPolicy freshnessPolicy = new StockFreshnessPolicy();
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
            StockIngestionService ingestion,
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
        this.ingestion = ingestion;
        this.clock = clock;
    }

    @Transactional
    public Optional<StockValuation> findBySymbol(String symbol) {
        Optional<InstrumentReference> instrumentOpt = referenceData.findActiveInstrumentBySymbol(symbol);
        if (instrumentOpt.isEmpty()) {
            return Optional.empty();
        }
        InstrumentReference instrument = instrumentOpt.orElseThrow();
        UUID instrumentId = instrument.instrumentId();
        Instant asOf = clock.instant();
        LocalDate asOfDate = LocalDate.now(clock);

        List<EquityDailyBarEntity> allSourceBars =
                dailyBars.findByInstrumentIdAndCurrentTrueOrderByTradingDateAsc(instrumentId);
        List<EquityDailyBarEntity> ascendingBars = dedupeByTradingDate(allSourceBars);
        EquityDailyBarEntity latestBar = ascendingBars.isEmpty() ? null : ascendingBars.get(ascendingBars.size() - 1);
        BigDecimal currentPrice = latestBar != null ? latestBar.getClosePrice() : null;

        Optional<EquityProfileEntity> profileOpt = profiles.findFirstByInstrumentIdAndEffectiveToIsNull(instrumentId);
        Long sharesOutstanding = profileOpt.map(EquityProfileEntity::getSharesOutstanding).orElse(null);

        var fundamentalsOpt = fundamentalReportService.findBySymbol(symbol);
        BigDecimal epsTtm = null;
        BigDecimal epsGrowth = null;
        BigDecimal equityParent = null;
        BigDecimal ebitdaTtm = null;
        BigDecimal totalDebt = null;
        BigDecimal cash = null;
        BigDecimal dividendTtm = null;
        DataStatus fundamentalsStatus = DataStatus.UNAVAILABLE;

        if (fundamentalsOpt.isPresent()) {
            var f = fundamentalsOpt.get();
            fundamentalsStatus = f.dataStatus();
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

        // DATA-005/NFR-007: price freshness is evaluated the same way as the
        // overview/technical sections (StockOverviewService,
        // TechnicalIndicatorService), not a bare null-check — a stale bar must
        // be able to trip the valuation-v1 PRICE_STALE publishability gate.
        var session = referenceData.resolveSession(instrument.venue(), asOf);
        DataStatus priceStatus = evaluatePriceFreshness(latestBar, session.tradingDate());
        DataStatus overallDataStatus = freshnessPolicy.evaluateValuationAssessment(
                List.of(priceStatus, fundamentalsStatus));

        List<FundamentalReportEntity> acceptedReports =
                reports.findAllByInstrumentIdAndCurrentTrueOrderByPeriodEndDesc(instrumentId);
        List<HistoryPoint> historyPoints = buildOwnHistorySeries(ascendingBars, acceptedReports, sharesOutstanding);

        // DATA-010: a material cross-source conflict on any date this
        // assessment actually consumes (the current price bar or a bar inside
        // the own-history window) must withhold the assessment, the same
        // reconciliation policy TechnicalIndicatorService applies to indicator
        // windows (T038).
        Set<LocalDate> conflictedDates = conflictedTradingDates(instrumentId, symbol, allSourceBars);
        boolean sourceConflict = !conflictedDates.isEmpty() && ascendingBars.stream()
                .skip(Math.max(0, ascendingBars.size() - MAX_HISTORY_BARS))
                .anyMatch(bar -> conflictedDates.contains(bar.getTradingDate()));

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
                // Fixture mode: sector cross-section basis stays disabled until
                // gate G-04 (research R-012) is owner-accepted; T063/T064.
                .sectorSeries(List.of())
                .priceDataStatus(priceStatus.name())
                .fundamentalsDataStatus(fundamentalsStatus.name())
                .sourceConflict(sourceConflict)
                .build();

        AssessmentResult result = engine.classify(inputs);

        UUID summaryId = fundamentalsOpt.map(FundamentalReportService.StockFundamentals::summaryId).orElse(null);
        UUID assessmentId = persistAssessment(
                instrumentId, asOfDate, asOf, result, overallDataStatus,
                latestBar, summaryId, profileOpt.orElse(null), sectorRef, historyPoints);

        List<String> coherenceParts = new ArrayList<>();
        coherenceParts.add("VALUATION");
        coherenceParts.add(symbol);
        if (assessmentId != null) {
            coherenceParts.add(assessmentId.toString());
        }
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
                overallDataStatus,
                result.reasonCodes(),
                asOfDate,
                asOf,
                coherenceKey
        ));
    }

    // Same source-conflict detection and single-row-per-date preference as
    // TechnicalIndicatorService (T038): a date is conflicted only when more
    // than one source is simultaneously accepted-current for it AND
    // SourceReconciliationService's policy finds a material divergence.
    private static final List<String> SOURCE_PREFERENCE = List.of("TCBS", "VNSTOCK", "FINVERA_FIXTURE");

    private static List<EquityDailyBarEntity> dedupeByTradingDate(List<EquityDailyBarEntity> rows) {
        Map<LocalDate, EquityDailyBarEntity> byDate = new LinkedHashMap<>();
        for (EquityDailyBarEntity row : rows) {
            byDate.merge(row.getTradingDate(), row, ValuationService::preferred);
        }
        return byDate.values().stream().sorted(Comparator.comparing(EquityDailyBarEntity::getTradingDate)).toList();
    }

    private static EquityDailyBarEntity preferred(EquityDailyBarEntity left, EquityDailyBarEntity right) {
        int leftRank = SOURCE_PREFERENCE.indexOf(left.getSource());
        int rightRank = SOURCE_PREFERENCE.indexOf(right.getSource());
        if (leftRank == -1) leftRank = SOURCE_PREFERENCE.size();
        if (rightRank == -1) rightRank = SOURCE_PREFERENCE.size();
        return leftRank <= rightRank ? left : right;
    }

    private Set<LocalDate> conflictedTradingDates(UUID instrumentId, String symbol,
            List<EquityDailyBarEntity> allSourceBars) {
        Map<LocalDate, Set<String>> sourcesByDate = new LinkedHashMap<>();
        for (EquityDailyBarEntity bar : allSourceBars) {
            sourcesByDate.computeIfAbsent(bar.getTradingDate(), d -> new LinkedHashSet<>()).add(bar.getSource());
        }
        Set<LocalDate> conflicted = new LinkedHashSet<>();
        for (var entry : sourcesByDate.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            var decision = ingestion.reconcileDailyBar(instrumentId, symbol, entry.getKey());
            if (decision == com.minhnb.finvera_be.market.domain.reconciliation.SourceReconciliationPolicy.Decision.SOURCE_CONFLICT) {
                conflicted.add(entry.getKey());
            }
        }
        return conflicted;
    }

    private DataStatus evaluatePriceFreshness(EquityDailyBarEntity latestBar, LocalDate sessionTradingDate) {
        if (latestBar == null) {
            return freshnessPolicy.evaluateMissing();
        }
        int sessionsBehind = countWeekdaysBetween(latestBar.getTradingDate(), sessionTradingDate);
        return freshnessPolicy.evaluateDailyBarSeries(sessionsBehind);
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

    /**
     * Basis A (own accepted ratio history), contracts/valuation-v1.md: PE, PB,
     * EV_EBITDA, and PEG are each evaluated at every one of the last
     * {@value #MAX_HISTORY_BARS} accepted sessions, using that session's own
     * accepted close and the fundamental figures that were actually observed
     * as of that session — not today's current EPS applied retroactively.
     *
     * <p>{@code sharesOutstanding} is held at its current effective value for
     * every historical point: {@code EquityProfileRepository} exposes no
     * point-in-time historical lookup, only the current effective revision
     * (a known simplification, consistent with how {@code StockOverviewService}
     * documents its own session-counting approximation).
     *
     * <p>Reuses {@link ValuationV1#computeMetrics} directly so the history
     * series and the "as of today" metrics can never disagree about what a
     * formula means.
     */
    private List<HistoryPoint> buildOwnHistorySeries(
            List<EquityDailyBarEntity> ascendingBars,
            List<FundamentalReportEntity> acceptedReports,
            Long sharesOutstanding) {
        if (ascendingBars.isEmpty() || acceptedReports.isEmpty()) {
            return List.of();
        }

        record ObservedReport(ReportPeriod period, Instant observedAt) {}
        List<ObservedReport> observedReports = acceptedReports.stream()
                .map(r -> new ObservedReport(toReportPeriod(r, reportMetrics.findByReportId(r.getId())), r.getObservedAt()))
                .toList();

        List<HistoryPoint> historyPoints = new ArrayList<>();
        int start = Math.max(0, ascendingBars.size() - MAX_HISTORY_BARS);
        for (int i = ascendingBars.size() - 1; i >= start; i--) {
            EquityDailyBarEntity bar = ascendingBars.get(i);
            if (bar.getClosePrice() == null) {
                continue;
            }
            Instant asOfBoundary = bar.getTradingDate().atTime(23, 59, 59).atZone(VN_ZONE).toInstant();
            List<ReportPeriod> visiblePeriods = observedReports.stream()
                    .filter(o -> o.observedAt() != null && !o.observedAt().isAfter(asOfBoundary))
                    .map(ObservedReport::period)
                    .toList();
            if (visiblePeriods.isEmpty()) {
                continue;
            }

            SummaryResult asOfSummary = historyCalculator.calculate(visiblePeriods, bar.getTradingDate());
            BigDecimal histEpsTtm = null;
            BigDecimal histEpsGrowth = null;
            BigDecimal histEquityParent = null;
            BigDecimal histEbitdaTtm = null;
            BigDecimal histTotalDebt = null;
            BigDecimal histCash = null;
            for (var m : asOfSummary.metrics()) {
                if (m.applicability() != MetricApplicability.DEFINED || m.value() == null) {
                    continue;
                }
                switch (m.metricCode()) {
                    case "EPS_TTM" -> histEpsTtm = m.value();
                    case "EPS_GROWTH_PERCENT" -> histEpsGrowth = m.value();
                    case "EQUITY_ATTRIBUTABLE_TO_PARENT" -> histEquityParent = m.value();
                    case "EBITDA_TTM" -> histEbitdaTtm = m.value();
                    case "TOTAL_DEBT" -> histTotalDebt = m.value();
                    case "CASH_AND_EQUIVALENTS" -> histCash = m.value();
                    default -> { /* not a valuation input */ }
                }
            }

            ComputedMetrics computed = ValuationV1.computeMetrics(Inputs.builder()
                    .price(bar.getClosePrice())
                    .sharesOutstanding(sharesOutstanding)
                    .epsTtm(histEpsTtm)
                    .epsGrowthPercent(histEpsGrowth)
                    .equityAttributableToParent(histEquityParent)
                    .ebitdaTtm(histEbitdaTtm)
                    .totalDebt(histTotalDebt)
                    .cashAndEquivalents(histCash)
                    .build());

            for (MetricValue mv : computed.allScored()) {
                if (mv.applicability() == MetricApplicability.DEFINED && mv.value() != null) {
                    historyPoints.add(new HistoryPoint(mv.metricCode(), mv.value()));
                }
            }
        }
        return historyPoints;
    }

    private static ReportPeriod toReportPeriod(FundamentalReportEntity r, List<FundamentalReportMetricEntity> rMetrics) {
        List<FundamentalSummaryCalculator.ReportMetric> metrics = rMetrics.stream()
                .map(m -> new FundamentalSummaryCalculator.ReportMetric(
                        m.getMetricCode(), m.getValue(), MetricApplicability.valueOf(m.getApplicability()),
                        m.getQualityReason()))
                .toList();
        return new ReportPeriod(
                r.getId(),
                r.getPeriodType(),
                r.getFiscalYear(),
                r.getFiscalQuarter() == null ? null : (int) r.getFiscalQuarter(),
                r.getPeriodStart(),
                r.getPeriodEnd(),
                r.getReportKind(),
                metrics);
    }

    /**
     * Persists {@code valuation_assessment} as an immutable revision chain
     * keyed by {@code (instrument_id, rule_version, as_of_trading_date)}, the
     * same per-date "current" scoping {@code TechnicalIndicatorService} uses
     * for {@code technical_indicator_result} (T038). Idempotent on unchanged
     * inputs: if the linked {@code PRICE_CURRENT} bar, {@code
     * FUNDAMENTAL_SUMMARY} revision, and own-history fingerprint all match the
     * existing current row for this date, nothing is written.
     */
    private UUID persistAssessment(
            UUID instrumentId,
            LocalDate asOfDate,
            Instant calculatedAt,
            AssessmentResult result,
            DataStatus dataStatus,
            EquityDailyBarEntity latestBar,
            UUID summaryId,
            EquityProfileEntity profile,
            SectorReferenceEntity sectorRef,
            List<HistoryPoint> historyPoints) {

        UUID dailyBarId = latestBar != null ? latestBar.getId() : null;
        String historyHash = historyPoints.isEmpty() ? null : sha256Hex(historyFingerprint(historyPoints));

        Optional<ValuationAssessmentEntity> existing = assessments
                .findFirstByInstrumentIdAndRuleVersionAndAsOfTradingDateAndCurrentTrue(
                        instrumentId, ValuationV1.RULE_VERSION, asOfDate);

        if (existing.isPresent() && inputsUnchanged(existing.get().getId(), dailyBarId, summaryId, historyHash)) {
            return existing.get().getId();
        }

        if (existing.isPresent()) {
            ValuationAssessmentEntity previous = existing.orElseThrow();
            previous.markSuperseded();
            assessments.saveAndFlush(previous);
        }

        UUID assessmentId = UUID.randomUUID();
        assessments.save(new ValuationAssessmentEntity(
                assessmentId, instrumentId, asOfDate, calculatedAt, ValuationV1.RULE_VERSION,
                result.classification() != null ? result.classification().name() : null,
                result.score(),
                result.displayedScore() != null ? result.displayedScore().shortValue() : null,
                result.confidence() != null ? result.confidence().shortValue() : null,
                result.usedOwnHistory(), result.usedSector(),
                sectorRef != null ? sectorRef.getId() : null,
                result.sectorConstituentCount() > 0 ? result.sectorConstituentCount() : null,
                result.historyPointCount() > 0 ? result.historyPointCount() : null,
                dataStatus.name(), result.reasonCodes(), calculatedAt, true,
                existing.map(ValuationAssessmentEntity::getId).orElse(null)));

        for (var metric : result.metrics()) {
            valuationMetrics.save(new ValuationMetricEntity(
                    assessmentId, metric.metricCode(), metric.value(), metric.applicability().name(),
                    metric.ownHistoryPercentile(), metric.sectorPercentile(), metric.effectiveWeight(),
                    metric.reasonCode()));
        }

        if (dailyBarId != null) {
            assessmentInputs.save(new ValuationAssessmentInputEntity(
                    assessmentId, "PRICE_CURRENT", null, dailyBarId, null, null, null));
        }
        if (summaryId != null) {
            assessmentInputs.save(new ValuationAssessmentInputEntity(
                    assessmentId, "FUNDAMENTAL_SUMMARY", null, null, summaryId, null, null));
        }
        if (profile != null) {
            assessmentInputs.save(new ValuationAssessmentInputEntity(
                    assessmentId, "EQUITY_PROFILE", null, null, null, profile.getId(), null));
        }
        if (historyHash != null) {
            assessmentInputs.save(new ValuationAssessmentInputEntity(
                    assessmentId, "OWN_HISTORY_SERIES", null, null, null, null, historyHash));
        }
        return assessmentId;
    }

    private boolean inputsUnchanged(UUID existingAssessmentId, UUID dailyBarId, UUID summaryId, String historyHash) {
        var existingInputs = assessmentInputs.findByAssessmentId(existingAssessmentId);
        UUID existingDailyBarId = existingInputs.stream()
                .filter(i -> "PRICE_CURRENT".equals(i.getInputRole()))
                .map(ValuationAssessmentInputEntity::getDailyBarId)
                .findFirst().orElse(null);
        UUID existingSummaryId = existingInputs.stream()
                .filter(i -> "FUNDAMENTAL_SUMMARY".equals(i.getInputRole()))
                .map(ValuationAssessmentInputEntity::getFundamentalSummaryId)
                .findFirst().orElse(null);
        String existingHistoryHash = existingInputs.stream()
                .filter(i -> "OWN_HISTORY_SERIES".equals(i.getInputRole()))
                .map(ValuationAssessmentInputEntity::getInputSetHash)
                .findFirst().orElse(null);
        return java.util.Objects.equals(existingDailyBarId, dailyBarId)
                && java.util.Objects.equals(existingSummaryId, summaryId)
                && java.util.Objects.equals(existingHistoryHash, historyHash);
    }

    private static String historyFingerprint(List<HistoryPoint> historyPoints) {
        StringBuilder sb = new StringBuilder();
        for (HistoryPoint hp : historyPoints) {
            sb.append(hp.metricCode()).append(':').append(hp.value().toPlainString()).append('|');
        }
        return sb.toString();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
