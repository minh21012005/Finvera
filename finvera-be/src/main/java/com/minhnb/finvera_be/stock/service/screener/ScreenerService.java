package com.minhnb.finvera_be.stock.service.screener;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalSummaryCalculator;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorComponent;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.ValuationMetricCode;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.CandidateFacts;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.CandidateResult;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.CategoryOutcome;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.CategoryStatus;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.DailyBarPoint;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.IndicatorSnapshot;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.MetricPoint;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.ScreenCriteria;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.FundamentalSummaryEntity;
import com.minhnb.finvera_be.stock.entity.FundamentalSummaryMetricEntity;
import com.minhnb.finvera_be.stock.entity.SectorReferenceEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorResultEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorValueEntity;
import com.minhnb.finvera_be.stock.entity.ValuationAssessmentEntity;
import com.minhnb.finvera_be.stock.entity.ValuationMetricEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalSummaryMetricRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalSummaryRepository;
import com.minhnb.finvera_be.stock.repository.SectorReferenceRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorResultRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorValueRepository;
import com.minhnb.finvera_be.stock.repository.ValuationAssessmentRepository;
import com.minhnb.finvera_be.stock.repository.ValuationMetricRepository;
import com.minhnb.finvera_be.stock.service.CoherenceKeys;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-001 to FR-013, DATA-001 to DATA-004, NFR-001, NFR-002 (research R-002,
 * R-007 to R-011). Orchestrates the two-pass, read-only screen execution:
 * pass 1 evaluates Market/Price against the full {@code LISTED} candidate
 * universe (one bulk fetch); pass 2 bulk-fetches Technical/Fundamental data
 * only for the pass-1 survivors and only for the categories the request
 * actually selected, then evaluates the full {@link ScreenCriteria} via
 * {@link ScreenerV1}, the single deterministic engine of record for every
 * filter (S-1, S-2).
 */
@Service
public class ScreenerService {

    /** Matches {@code screener-v1.md}'s 20-session Breakout lookback plus the as-of bar. */
    private static final int MAX_BARS_PER_INSTRUMENT = 21;
    private static final String LISTED = "LISTED";

    private final EquityProfileRepository equityProfiles;
    private final EquityDailyBarRepository dailyBars;
    private final SectorReferenceRepository sectorReferences;
    private final TechnicalIndicatorResultRepository technicalResults;
    private final TechnicalIndicatorValueRepository technicalValues;
    private final FundamentalSummaryRepository fundamentalSummaries;
    private final FundamentalSummaryMetricRepository fundamentalSummaryMetrics;
    private final ValuationAssessmentRepository valuationAssessments;
    private final ValuationMetricRepository valuationMetricsRepo;
    private final MarketReferenceDataService marketReferenceData;

    public ScreenerService(
            EquityProfileRepository equityProfiles,
            EquityDailyBarRepository dailyBars,
            SectorReferenceRepository sectorReferences,
            TechnicalIndicatorResultRepository technicalResults,
            TechnicalIndicatorValueRepository technicalValues,
            FundamentalSummaryRepository fundamentalSummaries,
            FundamentalSummaryMetricRepository fundamentalSummaryMetrics,
            ValuationAssessmentRepository valuationAssessments,
            ValuationMetricRepository valuationMetricsRepo,
            MarketReferenceDataService marketReferenceData) {
        this.equityProfiles = equityProfiles;
        this.dailyBars = dailyBars;
        this.sectorReferences = sectorReferences;
        this.technicalResults = technicalResults;
        this.technicalValues = technicalValues;
        this.fundamentalSummaries = fundamentalSummaries;
        this.fundamentalSummaryMetrics = fundamentalSummaryMetrics;
        this.valuationAssessments = valuationAssessments;
        this.valuationMetricsRepo = valuationMetricsRepo;
        this.marketReferenceData = marketReferenceData;
    }

    @Transactional(readOnly = true)
    public ScreenExecutionResult execute(ScreenCriteria criteria, SortField sortField, SortDirection sortDirection,
            int limit, int offset) {
        validateRanges(criteria);

        // ── Pass 1: the full LISTED universe, Market/Price evaluated in Java ──
        List<EquityProfileEntity> profiles = equityProfiles.findByEffectiveToIsNullAndListingStatus(LISTED);
        List<UUID> allInstrumentIds = profiles.stream().map(EquityProfileEntity::getInstrumentId).toList();
        Map<UUID, InstrumentReference> instrumentsById = marketReferenceData.findInstrumentsByIds(allInstrumentIds)
                .stream().collect(Collectors.toMap(InstrumentReference::instrumentId, r -> r));

        List<UUID> sectorIds = profiles.stream().map(EquityProfileEntity::getSectorReferenceId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, SectorReferenceEntity> sectorsById = sectorReferences.findAllById(sectorIds).stream()
                .collect(Collectors.toMap(SectorReferenceEntity::getId, s -> s));

        List<EquityDailyBarEntity> bars = dailyBars
                .findLatestNCurrentByInstrumentIdIn(allInstrumentIds, MAX_BARS_PER_INSTRUMENT);
        Map<UUID, List<EquityDailyBarEntity>> barsByInstrument = bars.stream()
                .collect(Collectors.groupingBy(EquityDailyBarEntity::getInstrumentId, LinkedHashMap::new,
                        Collectors.toList()));

        ScreenCriteria pass1Criteria = new ScreenCriteria(criteria.market(), criteria.price(), null, null);
        List<CandidateFacts> pass1Facts = new ArrayList<>();
        List<CategoryOutcome> marketOutcomes = new ArrayList<>();
        List<CategoryOutcome> priceOutcomes = new ArrayList<>();
        List<CandidateFacts> survivors = new ArrayList<>();

        for (EquityProfileEntity profile : profiles) {
            CandidateFacts lite = buildLiteCandidate(profile, instrumentsById, sectorsById,
                    barsByInstrument.getOrDefault(profile.getInstrumentId(), List.of()));
            pass1Facts.add(lite);
            CandidateResult pass1Result = ScreenerV1.evaluate(lite, pass1Criteria);
            for (CategoryOutcome outcome : pass1Result.categoryOutcomes()) {
                if ("MARKET".equals(outcome.category())) {
                    marketOutcomes.add(outcome);
                } else if ("PRICE".equals(outcome.category())) {
                    priceOutcomes.add(outcome);
                }
            }
            if (pass1Result.matched()) {
                survivors.add(lite);
            }
        }

        // ── Pass 2: bulk-fetch Technical/Fundamental only for survivors and only
        //     for the categories the request actually selected ──────────────────
        List<UUID> survivorIds = survivors.stream().map(CandidateFacts::instrumentId).toList();

        Map<UUID, Map<IndicatorCode, IndicatorSnapshot>> technicalByInstrument = Map.of();
        if (criteria.technical() != null && !survivorIds.isEmpty()) {
            technicalByInstrument = fetchTechnicalIndicators(survivorIds);
        }

        Map<UUID, Map<String, MetricPoint>> fundamentalByInstrument = Map.of();
        Map<UUID, Boolean> valuationPublishedByInstrument = Map.of();
        Map<UUID, Map<ValuationMetricCode, MetricPoint>> valuationByInstrument = Map.of();
        if (criteria.fundamental() != null && !survivorIds.isEmpty()) {
            fundamentalByInstrument = fetchFundamentalMetrics(survivorIds);
            var valuationFacts = fetchValuationMetrics(survivorIds);
            valuationPublishedByInstrument = valuationFacts.published();
            valuationByInstrument = valuationFacts.metrics();
        }

        List<CandidateResult> finalResults = new ArrayList<>();
        List<CategoryOutcome> technicalOutcomes = new ArrayList<>();
        List<CategoryOutcome> fundamentalOutcomes = new ArrayList<>();
        Map<UUID, CandidateFacts> fullFactsById = new HashMap<>();

        for (CandidateFacts lite : survivors) {
            CandidateFacts full = new CandidateFacts(
                    lite.instrumentId(), lite.symbol(), lite.companyName(), lite.exchange(), lite.sectorId(),
                    lite.sectorName(), lite.sharesOutstanding(), lite.asOfTradingDate(), lite.priceDataStatus(),
                    lite.latestClose(), lite.previousValidClose(), lite.latestVolume(), lite.recentBars(),
                    technicalByInstrument.getOrDefault(lite.instrumentId(), Map.of()),
                    fundamentalByInstrument.getOrDefault(lite.instrumentId(), Map.of()),
                    valuationPublishedByInstrument.getOrDefault(lite.instrumentId(), false),
                    valuationByInstrument.getOrDefault(lite.instrumentId(), Map.of()));
            fullFactsById.put(lite.instrumentId(), full);
            CandidateResult result = ScreenerV1.evaluate(full, criteria);
            finalResults.add(result);
            for (CategoryOutcome outcome : result.categoryOutcomes()) {
                if ("TECHNICAL".equals(outcome.category())) {
                    technicalOutcomes.add(outcome);
                } else if ("FUNDAMENTAL".equals(outcome.category())) {
                    fundamentalOutcomes.add(outcome);
                }
            }
        }

        List<ScreenMatch> matches = new ArrayList<>();
        List<String> coherenceParts = new ArrayList<>();
        for (CandidateResult result : finalResults) {
            if (!result.matched()) {
                continue;
            }
            CandidateFacts facts = fullFactsById.get(result.instrumentId());
            matches.add(new ScreenMatch(facts.symbol(), facts.companyName(), facts.exchange(), facts.sectorName(),
                    result.matchedValues(), facts.priceDataStatus(), facts.asOfTradingDate()));
            coherenceParts.add(facts.instrumentId().toString());
        }

        List<CategoryDisclosure> disclosures = new ArrayList<>();
        if (criteria.market() != null) {
            disclosures.add(disclosureFor("MARKET", marketOutcomes));
        }
        if (criteria.price() != null) {
            disclosures.add(disclosureFor("PRICE", priceOutcomes));
        }
        if (criteria.technical() != null) {
            disclosures.add(disclosureFor("TECHNICAL", technicalOutcomes));
        }
        if (criteria.fundamental() != null) {
            disclosures.add(disclosureFor("FUNDAMENTAL", fundamentalOutcomes));
        }

        List<ScreenMatch> sorted = sortMatches(matches, sortField, sortDirection);
        int totalMatchCount = sorted.size();
        int fromIndex = Math.min(offset, totalMatchCount);
        int toIndex = Math.min(fromIndex + limit, totalMatchCount);
        List<ScreenMatch> page = sorted.subList(fromIndex, toIndex);

        String coherenceKey = CoherenceKeys.of(coherenceParts);
        return new ScreenExecutionResult(page, totalMatchCount, disclosures, coherenceKey, Instant.now());
    }

    // ── Pass-1 candidate assembly ────────────────────────────────────────────

    private CandidateFacts buildLiteCandidate(EquityProfileEntity profile,
            Map<UUID, InstrumentReference> instrumentsById, Map<UUID, SectorReferenceEntity> sectorsById,
            List<EquityDailyBarEntity> orderedBars) {
        InstrumentReference instrument = instrumentsById.get(profile.getInstrumentId());
        String exchange = instrument != null ? instrument.venue() : null;
        String symbol = instrument != null ? instrument.symbol() : null;
        SectorReferenceEntity sector = profile.getSectorReferenceId() == null ? null
                : sectorsById.get(profile.getSectorReferenceId());

        LocalDate asOfTradingDate = null;
        BigDecimal latestClose = null;
        BigDecimal previousValidClose = null;
        Long latestVolume = null;
        List<DailyBarPoint> recentBars = List.of();
        // Documented simplification (mirrors StockOverviewService's precedent): price
        // freshness here is presence-based (a bar exists or it does not), not run
        // through StockFreshnessPolicy's session-count/contracted-delay evaluation.
        DataStatus priceDataStatus = DataStatus.UNAVAILABLE;

        if (!orderedBars.isEmpty()) {
            EquityDailyBarEntity latest = orderedBars.get(orderedBars.size() - 1);
            asOfTradingDate = latest.getTradingDate();
            latestClose = latest.getClosePrice();
            latestVolume = latest.getVolume();
            priceDataStatus = DataStatus.CURRENT;
            if (orderedBars.size() >= 2) {
                previousValidClose = orderedBars.get(orderedBars.size() - 2).getClosePrice();
            }
            recentBars = orderedBars.stream()
                    .map(b -> new DailyBarPoint(b.getTradingDate(), b.getClosePrice(), b.getHighPrice(),
                            b.getLowPrice()))
                    .toList();
        }

        return new CandidateFacts(profile.getInstrumentId(), symbol, profile.getCompanyNameVi(), exchange,
                profile.getSectorReferenceId(), sector != null ? sector.getDisplayNameVi() : null,
                profile.getSharesOutstanding(), asOfTradingDate, priceDataStatus, latestClose, previousValidClose,
                latestVolume, recentBars, Map.of(), Map.of(), false, Map.of());
    }

    // ── Pass-2 bulk fetches ──────────────────────────────────────────────────

    private Map<UUID, Map<IndicatorCode, IndicatorSnapshot>> fetchTechnicalIndicators(List<UUID> instrumentIds) {
        List<TechnicalIndicatorResultEntity> results = technicalResults
                .findLatestCurrentByInstrumentIdInAndRuleVersion(instrumentIds, TechnicalIndicatorsV1.RULE_VERSION);
        List<UUID> resultIds = results.stream().map(TechnicalIndicatorResultEntity::getId).toList();
        Map<UUID, List<TechnicalIndicatorValueEntity>> valuesByResultId = technicalValues.findByResultIdIn(resultIds)
                .stream().collect(Collectors.groupingBy(TechnicalIndicatorValueEntity::getResultId));

        Map<UUID, Map<IndicatorCode, IndicatorSnapshot>> byInstrument = new HashMap<>();
        for (TechnicalIndicatorResultEntity result : results) {
            IndicatorCode code;
            try {
                code = IndicatorCode.valueOf(result.getIndicatorCode());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Map<IndicatorComponent, BigDecimal> components = new EnumMap<>(IndicatorComponent.class);
            for (TechnicalIndicatorValueEntity value : valuesByResultId.getOrDefault(result.getId(), List.of())) {
                try {
                    components.put(IndicatorComponent.valueOf(value.getComponentCode()), value.getValue());
                } catch (IllegalArgumentException ignored) {
                    // Forward-compatible with a component this rule version does not know yet.
                }
            }
            IndicatorSnapshot snapshot = new IndicatorSnapshot(
                    com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability
                            .valueOf(result.getDataStatus().equals("CURRENT") || result.getQualityReason() == null
                                    ? "DEFINED" : "MISSING"),
                    components, result.getQualityReason());
            byInstrument.computeIfAbsent(result.getInstrumentId(), k -> new EnumMap<>(IndicatorCode.class))
                    .put(code, snapshot);
        }
        return byInstrument;
    }

    private Map<UUID, Map<String, MetricPoint>> fetchFundamentalMetrics(List<UUID> instrumentIds) {
        List<FundamentalSummaryEntity> summaries = fundamentalSummaries
                .findLatestByInstrumentIdInAndRuleVersion(instrumentIds, FundamentalSummaryCalculator.RULE_VERSION);
        Map<UUID, UUID> summaryIdByInstrument = summaries.stream()
                .collect(Collectors.toMap(FundamentalSummaryEntity::getInstrumentId, FundamentalSummaryEntity::getId));
        Map<UUID, UUID> instrumentBySummaryId = new HashMap<>();
        summaryIdByInstrument.forEach((instrumentId, summaryId) -> instrumentBySummaryId.put(summaryId, instrumentId));

        List<FundamentalSummaryMetricEntity> metricRows = fundamentalSummaryMetrics
                .findBySummaryIdIn(summaryIdByInstrument.values());

        Map<UUID, Map<String, MetricPoint>> byInstrument = new HashMap<>();
        for (FundamentalSummaryMetricEntity row : metricRows) {
            UUID instrumentId = instrumentBySummaryId.get(row.getSummaryId());
            if (instrumentId == null) {
                continue;
            }
            byInstrument.computeIfAbsent(instrumentId, k -> new HashMap<>()).put(row.getMetricCode(),
                    new MetricPoint(
                            com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability
                                    .valueOf(row.getApplicability()),
                            row.getValue(), row.getQualityReason()));
        }
        return byInstrument;
    }

    private record ValuationFacts(Map<UUID, Boolean> published, Map<UUID, Map<ValuationMetricCode, MetricPoint>> metrics) {
    }

    private ValuationFacts fetchValuationMetrics(List<UUID> instrumentIds) {
        List<ValuationAssessmentEntity> assessments = valuationAssessments
                .findLatestCurrentByInstrumentIdInAndRuleVersion(instrumentIds, ValuationV1.RULE_VERSION);
        Map<UUID, Boolean> published = new HashMap<>();
        Map<UUID, UUID> instrumentByAssessmentId = new HashMap<>();
        for (ValuationAssessmentEntity assessment : assessments) {
            published.put(assessment.getInstrumentId(), assessment.getClassification() != null);
            instrumentByAssessmentId.put(assessment.getId(), assessment.getInstrumentId());
        }

        List<ValuationMetricEntity> metricRows = valuationMetricsRepo
                .findByAssessmentIdIn(instrumentByAssessmentId.keySet());
        Map<UUID, Map<ValuationMetricCode, MetricPoint>> byInstrument = new HashMap<>();
        for (ValuationMetricEntity row : metricRows) {
            UUID instrumentId = instrumentByAssessmentId.get(row.getAssessmentId());
            if (instrumentId == null) {
                continue;
            }
            ValuationMetricCode code;
            try {
                code = ValuationMetricCode.valueOf(row.getMetricCode());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            byInstrument.computeIfAbsent(instrumentId, k -> new EnumMap<>(ValuationMetricCode.class)).put(code,
                    new MetricPoint(
                            com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability
                                    .valueOf(row.getApplicability()),
                            row.getValue(), row.getQualityReason()));
        }
        return new ValuationFacts(published, byInstrument);
    }

    // ── Aggregation, sorting, validation ─────────────────────────────────────

    private CategoryDisclosure disclosureFor(String category, List<CategoryOutcome> outcomes) {
        if (outcomes.isEmpty()) {
            return new CategoryDisclosure(category, DataStatus.UNAVAILABLE, "NO_CANDIDATES", 0);
        }
        long unavailableCount = outcomes.stream().filter(o -> o.status() == CategoryStatus.UNAVAILABLE).count();
        String reasonCode = outcomes.stream().filter(o -> o.status() == CategoryStatus.UNAVAILABLE)
                .map(CategoryOutcome::reasonCode).findFirst().orElse(null);
        DataStatus status;
        if (unavailableCount == outcomes.size()) {
            status = DataStatus.UNAVAILABLE;
        } else if (unavailableCount > 0) {
            status = DataStatus.PARTIAL;
        } else {
            status = DataStatus.CURRENT;
        }
        return new CategoryDisclosure(category, status, reasonCode, (int) unavailableCount);
    }

    private List<ScreenMatch> sortMatches(List<ScreenMatch> matches, SortField sortField,
            SortDirection sortDirection) {
        Comparator<ScreenMatch> comparator;
        if (sortField == SortField.SYMBOL) {
            comparator = Comparator.comparing(ScreenMatch::symbol);
        } else {
            String key = sortField.matchedValueKey();
            comparator = Comparator.comparing(
                    (ScreenMatch m) -> {
                        String raw = m.matchedValues().get(key);
                        return raw == null ? null : new BigDecimal(raw);
                    },
                    Comparator.nullsLast(Comparator.naturalOrder()));
        }
        if (sortDirection == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        List<ScreenMatch> sorted = new ArrayList<>(matches);
        sorted.sort(comparator.thenComparing(ScreenMatch::symbol));
        return sorted;
    }

    private void validateRanges(ScreenCriteria criteria) {
        if (criteria.market() != null) {
            ScreenerV1.validateRange(criteria.market().marketCapMin(), criteria.market().marketCapMax());
        }
        if (criteria.price() != null) {
            ScreenerV1.validateRange(criteria.price().priceMin(), criteria.price().priceMax());
            ScreenerV1.validateRange(criteria.price().priceChangePercentMin(),
                    criteria.price().priceChangePercentMax());
        }
        if (criteria.technical() != null) {
            var t = criteria.technical();
            ScreenerV1.validateRange(t.rsiMin(), t.rsiMax());
            ScreenerV1.validateRange(t.relativeVolumeMin(), t.relativeVolumeMax());
            if (t.volumeMin() != null && t.volumeMax() != null && t.volumeMin() > t.volumeMax()) {
                throw new IllegalArgumentException("INVALID_FILTER_RANGE");
            }
        }
        if (criteria.fundamental() != null) {
            var f = criteria.fundamental();
            ScreenerV1.validateRange(f.revenueGrowthPercentMin(), f.revenueGrowthPercentMax());
            ScreenerV1.validateRange(f.earningsGrowthPercentMin(), f.earningsGrowthPercentMax());
            ScreenerV1.validateRange(f.roeMin(), f.roeMax());
            ScreenerV1.validateRange(f.roaMin(), f.roaMax());
            ScreenerV1.validateRange(f.peMin(), f.peMax());
            ScreenerV1.validateRange(f.pbMin(), f.pbMax());
            ScreenerV1.validateRange(f.debtToEquityMin(), f.debtToEquityMax());
        }
    }

    // ── Result types ──────────────────────────────────────────────────────────

    public enum SortField {
        SYMBOL(null), MARKET_CAP("marketCap"), PRICE("price"), PRICE_CHANGE_PERCENT("priceChangePercent"),
        RSI("rsi"), RELATIVE_VOLUME("relativeVolume"), REVENUE_GROWTH_PERCENT("revenueGrowthPercent"),
        EARNINGS_GROWTH_PERCENT("earningsGrowthPercent"), ROE("roe"), ROA("roa"), PE("pe"), PB("pb"),
        DEBT_TO_EQUITY("debtToEquity");

        private final String matchedValueKey;

        SortField(String matchedValueKey) {
            this.matchedValueKey = matchedValueKey;
        }

        public String matchedValueKey() {
            return matchedValueKey;
        }
    }

    public enum SortDirection { ASC, DESC }

    public record ScreenMatch(String symbol, String companyName, String exchange, String sectorName,
            Map<String, String> matchedValues, DataStatus dataStatus, LocalDate asOfTradingDate) {
    }

    public record CategoryDisclosure(String category, DataStatus status, String reasonCode, int excludedCount) {
    }

    public record ScreenExecutionResult(List<ScreenMatch> matches, int totalMatchCount,
            List<CategoryDisclosure> categoryDisclosures, String coherenceKey, Instant calculatedAt) {
    }
}
