package com.minhnb.finvera_be.market.domain.regime;

import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.CURRENT;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.DELAYED;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel.BEAR;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel.BULL;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel.EARLY_BEAR;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel.EARLY_BULL;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel.SIDEWAYS;
import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV1.Component;
import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV1.ComponentScore;
import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV1.InputAvailability;
import com.minhnb.finvera_be.market.domain.regime.math.DecimalTimeSeries;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Replay and numerical boundaries for the immutable {@code market-regime-v1} rule. */
class MarketRegimeV1Tests {

    private final MarketRegimeV1 regime = new MarketRegimeV1();

    @Test
    void exposesVersionedBoundaryAndPublishabilityFixtures() throws IOException {
        assertThat(resource("fixtures/market/regime/market-regime-v1-label-boundaries.json"))
                .contains("market-regime-v1-boundaries-v1", "29.000000", "71.000000");
        assertThat(resource("fixtures/market/regime/market-regime-v1-publishability.json"))
                .contains("market-regime-v1-publishability-v1", "MISSING_BREADTH_AD_RATIO");
    }

    @Test
    void calculatesDecimalTimeSeriesWithoutBinaryFloatingPoint() {
        assertDecimal(DecimalTimeSeries.sma(decimals("10", "11", "12"), 3), "11.000000");
        assertDecimal(DecimalTimeSeries.simpleReturn(decimal("110"), decimal("100")), "0.100000");
        assertDecimal(DecimalTimeSeries.median(decimals("10", "20", "30", "40")), "25.000000");
        assertDecimal(DecimalTimeSeries.populationStandardDeviation(decimals("1", "2", "3")), "0.816497");
        assertDecimal(DecimalTimeSeries.percentileMidRank(decimal("2"), decimals("1", "2", "2", "3")), "50.000000");
    }

    @Test
    void calculatesWilderRsiWithDecimalGainsAndLosses() {
        assertDecimal(DecimalTimeSeries.wilderRsi(decimals(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"), 14), "100.000000");
        assertDecimal(DecimalTimeSeries.wilderRsi(decimals(
                "15", "14", "13", "12", "11", "10", "9", "8", "7", "6", "5", "4", "3", "2", "1"), 14), "0.000000");
    }

    @Test
    void assignsEveryApprovedLabelBoundaryExactly() {
        assertThat(regime.labelFor(decimal("29"))).isEqualTo(BEAR);
        assertThat(regime.labelFor(decimal("30"))).isEqualTo(EARLY_BEAR);
        assertThat(regime.labelFor(decimal("44"))).isEqualTo(EARLY_BEAR);
        assertThat(regime.labelFor(decimal("45"))).isEqualTo(SIDEWAYS);
        assertThat(regime.labelFor(decimal("55"))).isEqualTo(SIDEWAYS);
        assertThat(regime.labelFor(decimal("56"))).isEqualTo(EARLY_BULL);
        assertThat(regime.labelFor(decimal("70"))).isEqualTo(EARLY_BULL);
        assertThat(regime.labelFor(decimal("71"))).isEqualTo(BULL);
    }

    @Test
    void calculatesComponentScoresAndTreatsZeroAdvanceDeclineDenominatorAsMissing() {
        assertDecimal(regime.trendScore(new MarketRegimeV1.TrendInput(
                decimal("101"), decimal("100"), decimal("99"), decimal("98"), decimal("97"))), "100.000000");
        assertDecimal(regime.breadthScore(new MarketRegimeV1.BreadthInput(3, 1, decimal("60"))).orElseThrow(), "67.500000");
        assertThat(regime.breadthScore(new MarketRegimeV1.BreadthInput(0, 0, decimal("60")))).isEmpty();
        assertDecimal(regime.momentumScore(decimal("70"), decimal("0.10")), "100.000000");
        assertDecimal(regime.liquidityScore(decimal("1.50")), "100.000000");
        assertDecimal(regime.volatilityScore(decimal("20")), "80.000000");
    }

    @Test
    void publishesWithOneMissingComponentAndRenormalizesItsWeight() {
        var assessment = regime.assess(List.of(
                        component(Component.TREND, "80"), component(Component.BREADTH, "60"),
                        component(Component.MOMENTUM, "50"), component(Component.LIQUIDITY, "40")),
                new InputAvailability(true, true, CURRENT, DELAYED));

        assertThat(assessment.dataStatus()).isEqualTo(DELAYED);
        assertDecimal(assessment.completeness(), "90.000000");
        assertThat(assessment.renormalized()).isTrue();
        assertThat(assessment.score()).isNotNull();
        assertThat(assessment.reasonCodes()).contains("RENORMALIZED_MISSING_VOLATILITY");
        assertThat(assessment.factors()).hasSize(4)
                .allSatisfy(factor -> assertThat(factor.effectiveWeight())
                        .isGreaterThan(factor.originalWeight()));
    }

    @Test
    void acceptsExactlyEightyPercentCompletenessButRejectsAnythingBelowIt() {
        assertThat(regime.meetsCompletenessThreshold(decimal("80.000000"))).isTrue();
        assertThat(regime.meetsCompletenessThreshold(decimal("79.999999"))).isFalse();
    }

    @Test
    void withholdsAssessmentWhenCompletenessFallsBelowEightyPercent() {
        var assessment = regime.assess(List.of(
                        component(Component.TREND, "80"), component(Component.BREADTH, "60"),
                        component(Component.MOMENTUM, "50")),
                new InputAvailability(true, true, CURRENT, CURRENT));

        assertThat(assessment.label()).isNull();
        assertThat(assessment.score()).isNull();
        assertThat(assessment.confidence()).isNull();
        assertThat(assessment.reasonCodes()).contains("INSUFFICIENT_COMPONENT_COMPLETENESS");
    }

    @Test
    void confidenceUsesCompletenessAgreementAndDistanceToNearestBoundary() {
        var assessment = regime.assess(List.of(
                        component(Component.TREND, "100"), component(Component.BREADTH, "100"),
                        component(Component.MOMENTUM, "100"), component(Component.LIQUIDITY, "100"),
                        component(Component.VOLATILITY, "100")),
                new InputAvailability(true, true, CURRENT, CURRENT));

        assertDecimal(assessment.completeness(), "100.000000");
        assertDecimal(assessment.factorAgreement(), "100.000000");
        assertDecimal(assessment.boundaryDistance(), "100.000000");
        assertThat(assessment.confidence()).isEqualTo(100);
    }

    private static ComponentScore component(Component component, String score) {
        return new ComponentScore(component, decimal(score));
    }

    private static List<BigDecimal> decimals(String... values) {
        return List.of(values).stream().map(MarketRegimeV1Tests::decimal).toList();
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static void assertDecimal(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }

    private static String resource(String name) throws IOException {
        try (var input = MarketRegimeV1Tests.class.getClassLoader().getResourceAsStream(name)) {
            return new String(input.readAllBytes());
        }
    }
}
