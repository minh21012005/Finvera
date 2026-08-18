package com.minhnb.finvera_be.stock.domain.valuation;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.ValuationLabel;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * FR-008, FR-009, FR-010, DATA-004, DATA-007.
 * Covers every required test vector from {@code contracts/valuation-v1.md}:
 * band boundaries, negative earnings, single-basis, no-basis,
 * weight renormalization, coverage floor, tie handling,
 * stale fundamentals, restatement, and replay determinism.
 *
 * <p>These tests run red before {@link ValuationV1} exists.
 */
class ValuationV1Tests {

    private static final String RULE_VERSION = "valuation-v1";

    // ─────────────────────────────────────────────────────────────────────────────
    // Contract test vector: Band boundaries
    // Unrounded scores 35.4, 35.5, 64.4, 64.5 must map to UNDER, FAIR, FAIR, OVER.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void score35_4MapsToUnderValued() {
        // score 35.4 → displayedScore 35 → UNDER_VALUED
        var classification = ValuationV1.classifyBand(new BigDecimal("35.4"));
        assertThat(classification.label()).isEqualTo(ValuationLabel.UNDER_VALUED);
        assertThat(classification.displayedScore()).isEqualTo(35);
        assertThat(classification.score()).isEqualByComparingTo(new BigDecimal("35.4"));
    }

    @Test
    void score35_5MapsToFairValued() {
        // score 35.5 → displayedScore 36 → FAIR_VALUED (not UNDER_VALUED)
        var classification = ValuationV1.classifyBand(new BigDecimal("35.5"));
        assertThat(classification.label()).isEqualTo(ValuationLabel.FAIR_VALUED);
        assertThat(classification.displayedScore()).isEqualTo(36);
    }

    @Test
    void score64_4MapsToFairValued() {
        // score 64.4 → displayedScore 64 → FAIR_VALUED (not OVER_VALUED)
        var classification = ValuationV1.classifyBand(new BigDecimal("64.4"));
        assertThat(classification.label()).isEqualTo(ValuationLabel.FAIR_VALUED);
        assertThat(classification.displayedScore()).isEqualTo(64);
    }

    @Test
    void score64_5MapsToOverValued() {
        // score 64.5 → displayedScore 65 → OVER_VALUED
        var classification = ValuationV1.classifyBand(new BigDecimal("64.5"));
        assertThat(classification.label()).isEqualTo(ValuationLabel.OVER_VALUED);
        assertThat(classification.displayedScore()).isEqualTo(65);
    }

    @Test
    void displayedScoreNeverContradictsClassification() {
        // Contract U-2: classification uses unrounded score; displayed integer must agree.
        for (var scoreStr : List.of("35.4", "35.5", "64.4", "64.5")) {
            var score = new BigDecimal(scoreStr);
            var classification = ValuationV1.classifyBand(score);
            if (classification.label() == ValuationLabel.UNDER_VALUED) {
                assertThat(classification.displayedScore()).isLessThan(36);
            } else if (classification.label() == ValuationLabel.OVER_VALUED) {
                assertThat(classification.displayedScore()).isGreaterThanOrEqualTo(65);
            } else {
                assertThat(classification.displayedScore()).isBetween(36, 64);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Contract test vector: Negative earnings
    // PE and PEG are NOT_APPLICABLE; PB still scores; nothing is negative or zero-filled.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void negativeEarningsMakesPeAndPegNotApplicable() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("25000.000000"))
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(new BigDecimal("-500.000000"))      // negative → PE NOT_APPLICABLE
                .epsGrowthPercent(null)                      // PEG NOT_APPLICABLE when PE is N/A
                .equityAttributableToParent(new BigDecimal("19500000000000.000000"))
                .ebitdaTtm(new BigDecimal("3800000000000.000000"))
                .totalDebt(new BigDecimal("2000000000000.000000"))
                .cashAndEquivalents(new BigDecimal("1200000000000.000000"))
                .dividendPerShareTtm(new BigDecimal("0.000000"))
                .ownHistorySeries(List.of())
                .sectorSeries(List.of())
                .build();

        var result = engine.classify(inputs);

        var peMetric = findMetric(result, "PE");
        assertThat(peMetric.applicability()).isEqualTo(MetricApplicability.NOT_APPLICABLE);
        assertThat(peMetric.value()).isNull();          // never a negative P/E (DATA-007)

        var pegMetric = findMetric(result, "PEG");
        assertThat(pegMetric.applicability()).isEqualTo(MetricApplicability.NOT_APPLICABLE);

        // PB should still compute
        var pbMetric = findMetric(result, "PB");
        assertThat(pbMetric.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(pbMetric.value()).isNotNull().isPositive();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Contract test vector: Single basis (sector thin at 7 constituents)
    // Only Basis A is used; SECTOR_BASIS_INSUFFICIENT is reported.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void sectorWith7ConstituentsBelowFloorExcludesBasisB() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("69200.000000"))
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(new BigDecimal("4580.000000"))
                .epsGrowthPercent(new BigDecimal("18.500000"))
                .equityAttributableToParent(new BigDecimal("42000000000000.000000"))
                .ebitdaTtm(new BigDecimal("8900000000000.000000"))
                .totalDebt(new BigDecimal("3000000000000.000000"))
                .cashAndEquivalents(new BigDecimal("7500000000000.000000"))
                .dividendPerShareTtm(new BigDecimal("2000.000000"))
                .ownHistorySeries(buildMinimalHistory(600))  // 600 points >= 500 threshold
                .sectorSeries(buildSectorSeries(7))          // 7 < N_min=8 → excluded
                .build();

        var result = engine.classify(inputs);

        assertThat(result.published()).isTrue();
        assertThat(result.usedSector()).isFalse();
        assertThat(result.usedOwnHistory()).isTrue();
        assertThat(result.reasonCodes()).contains("SECTOR_BASIS_INSUFFICIENT");
        // Disclosure: only Basis A used
        assertThat(result.usedBases()).containsExactly("OWN_HISTORY");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Contract test vector: No basis — both bases unavailable
    // Assessment withheld; classification, score, confidence all null.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void noBasisWithholdsClassificationScoreAndConfidenceTogether() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("69200.000000"))
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(new BigDecimal("4580.000000"))
                .epsGrowthPercent(new BigDecimal("18.500000"))
                .equityAttributableToParent(new BigDecimal("42000000000000.000000"))
                .ebitdaTtm(new BigDecimal("8900000000000.000000"))
                .totalDebt(new BigDecimal("3000000000000.000000"))
                .cashAndEquivalents(new BigDecimal("7500000000000.000000"))
                .dividendPerShareTtm(new BigDecimal("2000.000000"))
                .ownHistorySeries(buildMinimalHistory(400)) // 400 < 500 → Basis A excluded
                .sectorSeries(buildSectorSeries(5))          // 5 < 8 → Basis B excluded
                .build();

        var result = engine.classify(inputs);

        assertThat(result.published()).isFalse();
        assertThat(result.classification()).isNull();
        assertThat(result.score()).isNull();
        assertThat(result.displayedScore()).isNull();
        assertThat(result.confidence()).isNull();
        assertThat(result.reasonCodes()).contains("NO_COMPARISON_BASIS");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Contract test vector: Weight renormalization
    // EV_EBITDA and PEG missing → PE and PB renormalize to 0.571428571429 and 0.428571428571 at scale 12.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void weightRenormalizationWhenEvEbitdaAndPegMissing() {
        // PE base weight 0.40, PB base weight 0.30; sum = 0.70
        // PE effective = 0.40 / 0.70 = 0.571428571428571... ≈ 0.571428571429 at scale 12
        // PB effective = 0.30 / 0.70 = 0.428571428571428... ≈ 0.428571428571 at scale 12
        var engine = new ValuationV1();
        // Inputs with MISSING ebitda and epsGrowthPercent to produce MISSING EV_EBITDA and PEG
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("69200.000000"))
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(new BigDecimal("4580.000000"))
                .epsGrowthPercent(null)                      // PEG will be MISSING
                .equityAttributableToParent(new BigDecimal("42000000000000.000000"))
                .ebitdaTtm(null)                             // EV_EBITDA will be MISSING
                .totalDebt(new BigDecimal("3000000000000.000000"))
                .cashAndEquivalents(new BigDecimal("7500000000000.000000"))
                .dividendPerShareTtm(new BigDecimal("2000.000000"))
                .ownHistorySeries(buildMinimalHistory(600))
                .sectorSeries(List.of())
                .build();

        var result = engine.classify(inputs);

        var peMetric = findMetric(result, "PE");
        var pbMetric = findMetric(result, "PB");
        assertThat(peMetric.effectiveWeight()).isEqualByComparingTo(new BigDecimal("0.571428571429"));
        assertThat(pbMetric.effectiveWeight()).isEqualByComparingTo(new BigDecimal("0.428571428571"));

        // EV_EBITDA and PEG should be MISSING with null effective weight
        var evEbitdaMetric = findMetric(result, "EV_EBITDA");
        assertThat(evEbitdaMetric.applicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(evEbitdaMetric.effectiveWeight()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Contract test vector: Coverage floor
    // Only PEG defined (weight 0.10 < 0.50) → withheld with INSUFFICIENT_METRIC_COVERAGE.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void onlyPegDefinedWithholdsWithInsufficientMetricCoverage() {
        // PEG base weight = 0.10, below the 0.50 floor → withhold
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("69200.000000"))
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(null)                                // PE → MISSING
                .epsGrowthPercent(new BigDecimal("18.500000")) // PEG needs PE (which is MISSING → PEG NOT_APPLICABLE)
                .equityAttributableToParent(null)            // PB → MISSING (bvps undefined)
                .ebitdaTtm(null)                             // EV_EBITDA → MISSING
                .totalDebt(null)
                .cashAndEquivalents(null)
                .dividendPerShareTtm(null)
                .ownHistorySeries(buildMinimalHistory(600))
                .sectorSeries(List.of())
                .build();

        var result = engine.classify(inputs);

        assertThat(result.published()).isFalse();
        assertThat(result.reasonCodes()).contains("INSUFFICIENT_METRIC_COVERAGE");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Contract test vector: Core metric unavailability
    // Neither PE nor PB defined → withheld with CORE_METRIC_UNAVAILABLE.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void neitherPeNorPbDefinedWithholdsCoreMetricUnavailable() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("69200.000000"))
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(new BigDecimal("-1000.000000"))  // PE → NOT_APPLICABLE
                .epsGrowthPercent(null)
                .equityAttributableToParent(new BigDecimal("-5000000000000.000000")) // PB NOT_APPLICABLE (bvps ≤ 0)
                .ebitdaTtm(new BigDecimal("8900000000000.000000"))
                .totalDebt(new BigDecimal("3000000000000.000000"))
                .cashAndEquivalents(new BigDecimal("7500000000000.000000"))
                .dividendPerShareTtm(new BigDecimal("2000.000000"))
                .ownHistorySeries(buildMinimalHistory(600))
                .sectorSeries(List.of())
                .build();

        var result = engine.classify(inputs);

        assertThat(result.published()).isFalse();
        assertThat(result.reasonCodes()).contains("CORE_METRIC_UNAVAILABLE");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Contract test vector: Tie handling
    // History where every point equals the current value → percentile = 50.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void seriesOfIdenticalValuesProducesPercentile50() {
        var engine = new ValuationV1();
        // Build a 600-point own-history series where every historical PE = current PE
        // percentileRank(x, S) = 100 * (count(s < x) + 0.5 * count(s = x)) / size(S)
        // All same value: count(s < x) = 0, count(s = x) = 600 → 100 * (0 + 300) / 600 = 50
        BigDecimal constantPe = new BigDecimal("10.000000000000");
        BigDecimal constantPb = new BigDecimal("2.000000000000");
        var historySeries = buildIdenticalHistory(600, constantPe, constantPb);
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("100.000000"))
                .sharesOutstanding(1_000_000L)
                .epsTtm(new BigDecimal("10.000000"))                                  // PE = 10
                .epsGrowthPercent(new BigDecimal("10.000000"))
                .equityAttributableToParent(new BigDecimal("50000000.000000"))       // bvps = 50 -> PB = 100/50 = 2
                .ebitdaTtm(new BigDecimal("5000000.000000"))
                .totalDebt(new BigDecimal("1000000.000000"))
                .cashAndEquivalents(new BigDecimal("1000000.000000"))
                .dividendPerShareTtm(new BigDecimal("2.000000"))
                .ownHistorySeries(historySeries)
                .sectorSeries(List.of())
                .build();

        var result = engine.classify(inputs);

        var peMetric = findMetric(result, "PE");
        // percentile at 50.0
        assertThat(peMetric.ownHistoryPercentile())
                .isEqualByComparingTo(new BigDecimal("50.000000000000"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Contract test vector: Stale fundamentals
    // A 300-day-old fundamental report withholds with FUNDAMENTALS_STALE.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void staleFundamentalsWithhold() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("69200.000000"))
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(new BigDecimal("4580.000000"))
                .epsGrowthPercent(new BigDecimal("18.500000"))
                .equityAttributableToParent(new BigDecimal("42000000000000.000000"))
                .ebitdaTtm(new BigDecimal("8900000000000.000000"))
                .totalDebt(new BigDecimal("3000000000000.000000"))
                .cashAndEquivalents(new BigDecimal("7500000000000.000000"))
                .dividendPerShareTtm(new BigDecimal("2000.000000"))
                .ownHistorySeries(buildMinimalHistory(600))
                .sectorSeries(List.of())
                .fundamentalsDataStatus("STALE")            // R-010: 300d old → STALE
                .build();

        var result = engine.classify(inputs);

        assertThat(result.published()).isFalse();
        assertThat(result.reasonCodes()).contains("FUNDAMENTALS_STALE");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Contract test vector: Restatement
    // A restated report produces a new assessment revision; superseded remains readable.
    // (This contract requirement is exercised by ValuationService integration tests in T052;
    //  here we verify the engine returns the same publishability shape for both inputs.)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void restatementProducesNewResultWithSameStructure() {
        var engine = new ValuationV1();
        var inputs = standardInputs();
        var result1 = engine.classify(inputs);
        // simulate a restated EPS (slightly different inputs)
        var restatementInputs = ValuationV1.Inputs.builder()
                .price(inputs.price())
                .sharesOutstanding(inputs.sharesOutstanding())
                .epsTtm(inputs.epsTtm().add(new BigDecimal("100.000000"))) // restated higher
                .epsGrowthPercent(inputs.epsGrowthPercent())
                .equityAttributableToParent(inputs.equityAttributableToParent())
                .ebitdaTtm(inputs.ebitdaTtm())
                .totalDebt(inputs.totalDebt())
                .cashAndEquivalents(inputs.cashAndEquivalents())
                .dividendPerShareTtm(inputs.dividendPerShareTtm())
                .ownHistorySeries(inputs.ownHistorySeries())
                .sectorSeries(inputs.sectorSeries())
                .build();
        var result2 = engine.classify(restatementInputs);
        // Both assessments must carry a published state and the correct rule version
        assertThat(result1.ruleVersion()).isEqualTo(RULE_VERSION);
        assertThat(result2.ruleVersion()).isEqualTo(RULE_VERSION);
        // The restated EPS produces a different score
        assertThat(result1.score()).isNotEqualByComparingTo(result2.score());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Contract test vector: Replay determinism
    // Recomputation from recorded inputs and rule_version yields the exact stored decimals.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void replayFromSameInputsProducesIdenticalDecimalResults() {
        var engine = new ValuationV1();
        var inputs = standardInputs();
        var firstRun = engine.classify(inputs);
        var secondRun = engine.classify(inputs);

        assertThat(firstRun.score()).isEqualByComparingTo(secondRun.score());
        assertThat(firstRun.classification()).isEqualTo(secondRun.classification());
        assertThat(firstRun.confidence()).isEqualTo(secondRun.confidence());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Disclosure
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void publishedAssessmentDisclosesBothUsedBases() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("69200.000000"))
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(new BigDecimal("4580.000000"))
                .epsGrowthPercent(new BigDecimal("18.500000"))
                .equityAttributableToParent(new BigDecimal("42000000000000.000000"))
                .ebitdaTtm(new BigDecimal("8900000000000.000000"))
                .totalDebt(new BigDecimal("3000000000000.000000"))
                .cashAndEquivalents(new BigDecimal("7500000000000.000000"))
                .dividendPerShareTtm(new BigDecimal("2000.000000"))
                .ownHistorySeries(buildMinimalHistory(600))
                .sectorSeries(buildSectorSeries(10))   // 10 >= 8 → sector basis available
                .build();
        var result = engine.classify(inputs);
        assertThat(result.published()).isTrue();
        assertThat(result.usedOwnHistory()).isTrue();
        assertThat(result.usedSector()).isTrue();
        assertThat(result.usedBases()).containsExactlyInAnyOrder("OWN_HISTORY", "SECTOR");
    }

    @Test
    void dividendYieldIsDisplayedButNotScored() {
        var engine = new ValuationV1();
        var inputs = standardInputs();
        var result = engine.classify(inputs);
        var dvMetric = findMetric(result, "DIVIDEND_YIELD");
        assertThat(dvMetric.applicability()).isEqualTo(MetricApplicability.DEFINED);
        // DIVIDEND_YIELD carries null effectiveWeight — it is displayed but not scored
        assertThat(dvMetric.effectiveWeight()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Confidence formula
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void confidenceIsWithinZeroToHundred() {
        var engine = new ValuationV1();
        var result = engine.classify(standardInputs());
        if (result.confidence() != null) {
            assertThat(result.confidence()).isBetween(0, 100);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private static List<ValuationV1.HistoryPoint> buildMinimalHistory(int size) {
        // Build a history series with PE and PB so qualifying metric coverage >= 0.50 (0.40 + 0.30 = 0.70)
        var list = new java.util.ArrayList<ValuationV1.HistoryPoint>();
        for (int i = 0; i < size; i++) {
            BigDecimal histPe = new BigDecimal("5.000000000000")
                    .add(new BigDecimal("15.000000000000").multiply(new BigDecimal(i))
                            .divide(new BigDecimal(size), 12, java.math.RoundingMode.HALF_UP));
            list.add(new ValuationV1.HistoryPoint("PE", histPe));

            BigDecimal histPb = new BigDecimal("1.000000000000")
                    .add(new BigDecimal("3.000000000000").multiply(new BigDecimal(i))
                            .divide(new BigDecimal(size), 12, java.math.RoundingMode.HALF_UP));
            list.add(new ValuationV1.HistoryPoint("PB", histPb));
        }
        return list;
    }

    private static List<ValuationV1.HistoryPoint> buildIdenticalHistory(int size, BigDecimal peVal, BigDecimal pbVal) {
        var list = new java.util.ArrayList<ValuationV1.HistoryPoint>();
        for (int i = 0; i < size; i++) {
            list.add(new ValuationV1.HistoryPoint("PE", peVal));
            list.add(new ValuationV1.HistoryPoint("PB", pbVal));
        }
        return list;
    }

    private static List<ValuationV1.SectorPoint> buildSectorSeries(int count) {
        var list = new java.util.ArrayList<ValuationV1.SectorPoint>();
        for (int i = 0; i < count; i++) {
            BigDecimal pe = new BigDecimal("10.000000000000").add(new BigDecimal(i));
            list.add(new ValuationV1.SectorPoint("INSTRUMENT_" + i, "PE", pe));
            BigDecimal pb = new BigDecimal("2.000000000000").add(new BigDecimal(i).divide(new BigDecimal(10), 12, java.math.RoundingMode.HALF_UP));
            list.add(new ValuationV1.SectorPoint("INSTRUMENT_" + i, "PB", pb));
        }
        return list;
    }

    private ValuationV1.Inputs standardInputs() {
        return ValuationV1.Inputs.builder()
                .price(new BigDecimal("69200.000000"))
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(new BigDecimal("4580.000000"))
                .epsGrowthPercent(new BigDecimal("18.500000"))
                .equityAttributableToParent(new BigDecimal("42000000000000.000000"))
                .ebitdaTtm(new BigDecimal("8900000000000.000000"))
                .totalDebt(new BigDecimal("3000000000000.000000"))
                .cashAndEquivalents(new BigDecimal("7500000000000.000000"))
                .dividendPerShareTtm(new BigDecimal("2000.000000"))
                .ownHistorySeries(buildMinimalHistory(600))
                .sectorSeries(buildSectorSeries(10))
                .build();
    }

    private ValuationV1.MetricResult findMetric(ValuationV1.AssessmentResult result, String code) {
        return result.metrics().stream()
                .filter(m -> m.metricCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Metric not found: " + code));
    }
}
