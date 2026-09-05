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

    private static final String RULE_VERSION = ValuationV1.RULE_VERSION;

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
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
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
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
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
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
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
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
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
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
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
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
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
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .build();

        var result = engine.classify(inputs);

        var peMetric = findMetric(result, "PE");
        // percentile at 50.0
        assertThat(peMetric.ownHistoryPercentile())
                .isEqualByComparingTo(new BigDecimal("50.000000000000"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Contract amendment 2026-09-01: Stale fundamentals is non-blocking
    // A 300-day-old fundamental report (STALE) still publishes with a warning.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void staleFundamentalsStillPublishesWithWarning() {
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
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .build();

        var result = engine.classify(inputs);

        assertThat(result.published()).isTrue();
        assertThat(result.classification()).isNotNull();
        assertThat(result.score()).isNotNull();
        assertThat(result.confidence()).isNotNull();
        assertThat(result.reasonCodes()).contains("FUNDAMENTALS_STALE");
    }

    @Test
    void fundamentalsUnavailableWithholds() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("69200.000000"))
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(null)
                .epsGrowthPercent(null)
                .equityAttributableToParent(null)
                .ebitdaTtm(null)
                .totalDebt(null)
                .cashAndEquivalents(null)
                .dividendPerShareTtm(null)
                .ownHistorySeries(List.of())
                .sectorSeries(List.of())
                .fundamentalsDataStatus("UNAVAILABLE")
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .build();

        var result = engine.classify(inputs);

        assertThat(result.published()).isFalse();
        assertThat(result.reasonCodes()).contains("FUNDAMENTALS_UNAVAILABLE");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Contract amendment 2026-09-01: PRICE_STALE is non-blocking
    // A stale price still publishes classification, score, and confidence,
    // with PRICE_STALE present in reason codes as a warning.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void stalePriceStillPublishesWithWarning() {
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
                .priceDataStatus("STALE")
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .build();

        var result = engine.classify(inputs);

        // Amended: PRICE_STALE no longer withholds; classification, score, and confidence are set.
        assertThat(result.published()).isTrue();
        assertThat(result.classification()).isNotNull();
        assertThat(result.score()).isNotNull();
        assertThat(result.confidence()).isNotNull();
        // PRICE_STALE is still disclosed in reason codes as a warning.
        assertThat(result.reasonCodes()).contains("PRICE_STALE");
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
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
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
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
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
    // DATA-007 / U-3: a missing price input and a genuinely zero price must
    // remain distinguishable for DIVIDEND_YIELD, exactly as they already are
    // for PE/PB/EV_EBITDA (MISSING_PRICE vs their own NOT_APPLICABLE reasons).
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void dividendYieldIsMissingNotNotApplicableWhenPriceIsAbsent() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(null)                                  // price input absent, not zero
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(new BigDecimal("4580.000000"))
                .epsGrowthPercent(new BigDecimal("18.500000"))
                .equityAttributableToParent(new BigDecimal("42000000000000.000000"))
                .ebitdaTtm(new BigDecimal("8900000000000.000000"))
                .totalDebt(new BigDecimal("3000000000000.000000"))
                .cashAndEquivalents(new BigDecimal("7500000000000.000000"))
                .dividendPerShareTtm(new BigDecimal("2000.000000"))
                .ownHistorySeries(List.of())
                .sectorSeries(List.of())
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .build();

        var result = engine.classify(inputs);

        var dvMetric = findMetric(result, "DIVIDEND_YIELD");
        assertThat(dvMetric.applicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(dvMetric.reasonCode()).isEqualTo("MISSING_PRICE");
        assertThat(dvMetric.value()).isNull();
    }

    @Test
    void dividendYieldIsNotApplicableWhenPriceIsExactlyZero() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(BigDecimal.ZERO)                       // price genuinely zero, not missing
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(new BigDecimal("4580.000000"))
                .epsGrowthPercent(new BigDecimal("18.500000"))
                .equityAttributableToParent(new BigDecimal("42000000000000.000000"))
                .ebitdaTtm(new BigDecimal("8900000000000.000000"))
                .totalDebt(new BigDecimal("3000000000000.000000"))
                .cashAndEquivalents(new BigDecimal("7500000000000.000000"))
                .dividendPerShareTtm(new BigDecimal("2000.000000"))
                .ownHistorySeries(List.of())
                .sectorSeries(List.of())
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .build();

        var result = engine.classify(inputs);

        var dvMetric = findMetric(result, "DIVIDEND_YIELD");
        assertThat(dvMetric.applicability()).isEqualTo(MetricApplicability.NOT_APPLICABLE);
        assertThat(dvMetric.reasonCode()).isEqualTo("ZERO_PRICE");
        assertThat(dvMetric.value()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // U-1: confidence must be computed decimal-only; this worked example pins
    // the exact integer so a future regression to double/float arithmetic
    // would have to produce the identical rounding to stay green.
    // standardInputs()'s history/sector series only carry PE and PB points, so
    // only those two metrics qualify: metricCoverage = 0.40+0.30 = 0.70;
    // basisCoverage = 2/2 = 1.00 (both bases score on PE+PB);
    // historyDepth = 600/750 = 0.80.
    // confidence = round(100 * (0.45*0.70 + 0.35*1.00 + 0.20*0.80))
    //            = round(100 * 0.825) = round(82.5) = 83 (HALF_UP)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void confidenceMatchesDecimalWorkedExample() {
        var engine = new ValuationV1();
        var result = engine.classify(standardInputs());
        assertThat(result.confidence()).isEqualTo(83);
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

    @Test
    void sectorOnlyBasisStillDisclosesEffectiveWeightsPerMetric() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("69200.000000"))
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(new BigDecimal("4580.000000"))
                .equityAttributableToParent(new BigDecimal("42000000000000.000000"))
                .ownHistorySeries(List.of())            // Basis A unavailable
                .sectorSeries(buildSectorSeries(12))    // Basis B qualifies (>= 8)
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .build();

        var result = engine.classify(inputs);

        assertThat(result.published()).isTrue();
        assertThat(result.usedBases()).containsExactly("SECTOR");
        var pe = result.metrics().stream().filter(m -> m.metricCode().equals("PE")).findFirst().orElseThrow();
        var pb = result.metrics().stream().filter(m -> m.metricCode().equals("PB")).findFirst().orElseThrow();
        // PE 0.40 and PB 0.30 renormalize over 0.70 -> 0.571428571429 / 0.428571428571 at scale 12.
        assertThat(pe.effectiveWeight()).isEqualByComparingTo("0.571428571429");
        assertThat(pb.effectiveWeight()).isEqualByComparingTo("0.428571428571");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // valuation-v3 (specs/023 contracts/valuation-v3.md): Basis A ranks a flow metric's
    // fiscal-year comparison value against a fiscal-year series, and discloses both.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void v3GrowthCompanyRanksTheFiscalYearValueNotTheTtmHeadline() {
        // VNM as stored 2026-08-28: TTM EPS 4,728 (four quarters), FY2025 EPS 4,028, close 62,300.
        var comparison = comparisonOf(ValuationV1.Inputs.builder()
                .price(new BigDecimal("62300")).sharesOutstanding(2_089_955_445L)
                .epsTtm(new BigDecimal("4028"))                                   // fiscal-year EPS
                .equityAttributableToParent(new BigDecimal("42000000000000"))
                .build());
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("62300")).sharesOutstanding(2_089_955_445L)
                .epsTtm(new BigDecimal("4728"))                                   // quarter-TTM headline
                .equityAttributableToParent(new BigDecimal("42000000000000"))
                .ownHistorySeries(buildMinimalHistory(600))
                .ownHistoryComparison(comparison)
                .sectorSeries(List.of())
                .build();

        var result = new ValuationV1().classify(inputs);

        assertThat(result.ruleVersion()).isEqualTo("valuation-v3");
        var pe = findMetric(result, "PE");
        assertThat(pe.value()).isEqualByComparingTo("13.176818950931");            // headline unchanged from v2
        assertThat(pe.ownHistoryBasis()).isEqualTo(ValuationV1.BASIS_FISCAL_YEAR);
        assertThat(pe.ownHistoryComparisonValue()).isEqualByComparingTo("15.466732869911");
        // buildMinimalHistory(600) is 5 + 15*i/600: 419 points lie below 15.4667 (69.833 %),
        // whereas the TTM headline 13.1768 would have ranked at 54.667 % — the bias being removed.
        assertThat(pe.ownHistoryPercentile()).isEqualByComparingTo("69.833333333333");
        var pb = findMetric(result, "PB");
        assertThat(pb.ownHistoryBasis()).isEqualTo(ValuationV1.BASIS_LATEST_REPORT);
        assertThat(pb.ownHistoryComparisonValue()).isEqualByComparingTo(pb.value());
        assertThat(result.reasonCodes()).contains(ValuationV1.HISTORY_FISCAL_YEAR_BASIS)
                .doesNotContain(ValuationV1.HISTORY_COMPARISON_UNAVAILABLE);
    }

    @Test
    void v3TurnaroundWithNegativeFiscalYearEpsGetsNoHistoryPercentileForPeAndSaysSo() {
        var comparison = comparisonOf(ValuationV1.Inputs.builder()
                .price(new BigDecimal("25000")).sharesOutstanding(1_000_000_000L)
                .epsTtm(new BigDecimal("-500"))                                   // FY EPS <= 0: no FY-basis PE
                .equityAttributableToParent(new BigDecimal("30000000000000"))
                .build());
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("25000")).sharesOutstanding(1_000_000_000L)
                .epsTtm(new BigDecimal("1000"))                                   // TTM EPS > 0: headline PE DEFINED
                .equityAttributableToParent(new BigDecimal("30000000000000"))
                .ownHistorySeries(buildMinimalHistory(600))
                .ownHistoryComparison(comparison)
                .sectorSeries(buildSectorSeries(10))                              // PE still qualifies through Basis B
                .build();

        var result = new ValuationV1().classify(inputs);

        var pe = findMetric(result, "PE");
        assertThat(pe.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(pe.ownHistoryPercentile()).isNull();                          // never defaulted to the headline
        assertThat(pe.ownHistoryBasis()).isEqualTo(ValuationV1.BASIS_FISCAL_YEAR);
        assertThat(pe.ownHistoryComparisonValue()).isNull();
        assertThat(pe.sectorPercentile()).isNotNull();
        assertThat(findMetric(result, "PB").ownHistoryPercentile()).isNotNull();
        assertThat(result.reasonCodes()).contains(ValuationV1.HISTORY_COMPARISON_UNAVAILABLE);
        assertThat(result.published()).isTrue();
    }

    @Test
    void v3AnnualOnlyCompanyReproducesTheHeadlineRank() {
        // No quarterly EPS (banks, brokers): the headline is already the annual figure, so the
        // fiscal-year comparison value equals it and v3 ranks exactly what v2 ranked (DATA-003).
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("25000")).sharesOutstanding(1_000_000_000L)
                .epsTtm(new BigDecimal("2000"))
                .equityAttributableToParent(new BigDecimal("30000000000000"))
                .ownHistorySeries(buildMinimalHistory(600))
                .sectorSeries(List.of())
                .ownHistoryComparisonFromHeadline()
                .build();

        var result = new ValuationV1().classify(inputs);

        var pe = findMetric(result, "PE");
        assertThat(pe.value()).isEqualByComparingTo("12.5");
        assertThat(pe.ownHistoryComparisonValue()).isEqualByComparingTo(pe.value());
        // 12.5 sits exactly on point i = 300 of 5 + 15*i/600: (300 + 0.5) * 100 / 600
        assertThat(pe.ownHistoryPercentile()).isEqualByComparingTo("50.083333333333");
    }

    @Test
    void v3ReplayReproducesBasisAndComparisonDecimalsExactly() {
        var comparison = comparisonOf(ValuationV1.Inputs.builder()
                .price(new BigDecimal("62300")).sharesOutstanding(2_089_955_445L)
                .epsTtm(new BigDecimal("4028")).equityAttributableToParent(new BigDecimal("42000000000000")).build());
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("62300")).sharesOutstanding(2_089_955_445L)
                .epsTtm(new BigDecimal("4728")).equityAttributableToParent(new BigDecimal("42000000000000"))
                .ownHistorySeries(buildMinimalHistory(600)).ownHistoryComparison(comparison).sectorSeries(List.of())
                .build();
        var engine = new ValuationV1();
        assertThat(engine.classify(inputs).metrics()).isEqualTo(engine.classify(inputs).metrics());
        assertThat(engine.classify(inputs).score()).isEqualByComparingTo(engine.classify(inputs).score());
    }

    /** What ValuationService hands the engine: every metric computed on the fiscal-year inputs. */
    private static java.util.Map<String, ValuationV1.MetricValue> comparisonOf(ValuationV1.Inputs fiscalYearInputs) {
        var map = new java.util.HashMap<String, ValuationV1.MetricValue>();
        for (var mv : ValuationV1.computeMetrics(fiscalYearInputs).allScored()) {
            map.put(mv.metricCode(), mv);
        }
        return map;
    }

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
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .build();
    }

    @Test
    void directBvpsAndDividendYieldComputesCorrectly() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("70000.000000"))
                .epsTtm(new BigDecimal("5000.000000")) // PE = 14.0
                .bvps(new BigDecimal("20000.000000")) // PB = 3.5
                .dividendYield(new BigDecimal("4.500000")) // Dividend Yield = 4.5%
                .sectorSeries(buildSectorSeries(10))
                .build();

        var result = engine.classify(inputs);
        assertThat(result.published()).isTrue();
        var pb = findMetric(result, "PB");
        assertThat(pb.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(pb.value()).isEqualByComparingTo(new BigDecimal("3.500000000000"));

        var pe = findMetric(result, "PE");
        assertThat(pe.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(pe.value()).isEqualByComparingTo(new BigDecimal("14.000000000000"));

        var divYield = findMetric(result, "DIVIDEND_YIELD");
        assertThat(divYield.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(divYield.value()).isEqualByComparingTo(new BigDecimal("4.500000"));
    }

    private ValuationV1.MetricResult findMetric(ValuationV1.AssessmentResult result, String code) {
        return result.metrics().stream()
                .filter(m -> m.metricCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Metric not found: " + code));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // valuation-v2 (specs/012 contracts/valuation-v2.md): NOT_APPLICABLE and the
    // structurally unobtainable EV/EBITDA leave the coverage denominator; MISSING stays.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void v2LossMakerPublishesOnPriceToBookWithReducedMetricSet() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("25000.000000"))
                .sharesOutstanding(1_000_000_000L)
                .epsTtm(new BigDecimal("-500.000000"))       // PE NOT_APPLICABLE, hence PEG NOT_APPLICABLE
                .epsGrowthPercent(new BigDecimal("12.000000"))
                .equityAttributableToParent(new BigDecimal("30000000000000.000000"))
                .ebitdaTtm(null)                              // EV_EBITDA MISSING_EBITDA (structural)
                .totalDebt(null).cashAndEquivalents(null)
                .ownHistorySeries(buildMinimalHistory(600))
                .sectorSeries(List.of())
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .build();
        var result = engine.classify(inputs);
        assertThat(result.ruleVersion()).isEqualTo(ValuationV1.RULE_VERSION);
        assertThat(result.published()).isTrue();
        assertThat(result.reasonCodes()).contains(ValuationV1.REDUCED_METRIC_SET)
                .doesNotContain("INSUFFICIENT_METRIC_COVERAGE");
        assertThat(findMetric(result, "PB").effectiveWeight()).isEqualByComparingTo("1.000000000000");
        assertThat(findMetric(result, "PE").applicability()).isEqualTo(MetricApplicability.NOT_APPLICABLE);
        // confidence keeps the ABSOLUTE metric coverage (0.30): 100*(0.45*0.30 + 0.35*0.5 + 0.20*min(600/750,1))
        // = 100*(0.135 + 0.175 + 0.16) = 47
        assertThat(result.confidence()).isEqualTo(47);
    }

    @Test
    void v2RealDataGapStillWithholdsWithInsufficientMetricCoverage() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("25000.000000"))
                .sharesOutstanding(1_000_000_000L)
                .epsTtm(null)                                 // PE MISSING_EPS -> a gap, stays in the denominator
                .epsGrowthPercent(new BigDecimal("12.000000"))
                .equityAttributableToParent(new BigDecimal("30000000000000.000000"))
                .ebitdaTtm(null)
                .totalDebt(null).cashAndEquivalents(null)
                .ownHistorySeries(buildMinimalHistory(600))
                .sectorSeries(List.of())
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .build();
        var result = engine.classify(inputs);
        // obtainable = PE 0.40 + PB 0.30 (PEG is NOT_APPLICABLE via PE_NOT_DEFINED, EV structural) -> 0.30/0.70 < 0.50
        assertThat(result.published()).isFalse();
        assertThat(result.reasonCodes()).contains("INSUFFICIENT_METRIC_COVERAGE");
    }

    @Test
    void v2PegNotApplicableAloneDoesNotFlagAReducedMetricSet() {
        var engine = new ValuationV1();
        var inputs = ValuationV1.Inputs.builder()
                .price(new BigDecimal("69200.000000"))
                .sharesOutstanding(1_462_000_000L)
                .epsTtm(new BigDecimal("4580.000000"))
                .epsGrowthPercent(new BigDecimal("-3.000000"))   // PEG NOT_APPLICABLE only
                .equityAttributableToParent(new BigDecimal("42000000000000.000000"))
                .ebitdaTtm(null).totalDebt(null).cashAndEquivalents(null)
                .ownHistorySeries(buildMinimalHistory(600))
                .sectorSeries(List.of())
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .build();
        var result = engine.classify(inputs);
        assertThat(result.published()).isTrue();
        assertThat(result.reasonCodes()).doesNotContain(ValuationV1.REDUCED_METRIC_SET);
    }

    @Test
    void psIsInformationalAndPricesLossMakersWherePeIsNotApplicable() {
        // Feature 022 (valuation-v2 addendum): PS = marketCap / revenueTtm, weight 0.
        var inputs = ValuationV1.Inputs.builder()
                .price(new java.math.BigDecimal("20000"))
                .sharesOutstanding(1_000_000L)
                .epsTtm(new java.math.BigDecimal("-500"))                 // loss-maker: PE NOT_APPLICABLE
                .bvps(new java.math.BigDecimal("15000"))
                .revenueTtm(new java.math.BigDecimal("100000000000"))     // 100 bn revenue
                .ownHistorySeries(java.util.List.of())
                .sectorSeries(java.util.List.of())
                .priceDataStatus("CURRENT")
                .fundamentalsDataStatus("CURRENT")
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .build();

        var computed = ValuationV1.computeMetrics(inputs);

        assertThat(computed.pe().applicability().name()).isEqualTo("NOT_APPLICABLE");
        assertThat(computed.ps().applicability().name()).isEqualTo("DEFINED");
        // marketCap 20,000 x 1,000,000 = 2e10; PS = 2e10 / 1e11 = 0.2
        assertThat(computed.ps().value()).isEqualByComparingTo(new java.math.BigDecimal("0.2"));
        assertThat(computed.allScored()).noneMatch(m -> m.metricCode().equals("PS"));  // weight 0, outside the composite

        var zeroRevenue = ValuationV1.Inputs.builder()
                .price(new java.math.BigDecimal("20000")).sharesOutstanding(1_000_000L)
                .revenueTtm(java.math.BigDecimal.ZERO)
                .ownHistorySeries(java.util.List.of()).sectorSeries(java.util.List.of())
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .priceDataStatus("CURRENT").fundamentalsDataStatus("CURRENT").build();
        assertThat(ValuationV1.computeMetrics(zeroRevenue).ps().applicability().name()).isEqualTo("NOT_APPLICABLE");
        var noRevenue = ValuationV1.Inputs.builder()
                .price(new java.math.BigDecimal("20000")).sharesOutstanding(1_000_000L)
                .ownHistorySeries(java.util.List.of()).sectorSeries(java.util.List.of())
                .ownHistoryComparisonFromHeadline()  // v3: annual-only inputs rank their headline (DATA-003)
                .priceDataStatus("CURRENT").fundamentalsDataStatus("CURRENT").build();
        assertThat(ValuationV1.computeMetrics(noRevenue).ps().qualityReason()).isEqualTo("MISSING_REVENUE");
    }
}
