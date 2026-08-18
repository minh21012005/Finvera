package com.minhnb.finvera_be.stock.domain.time;

import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.CURRENT;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.DELAYED;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.STALE;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Boundary tests for every dataset row in research.md R-010, both sides of each threshold. */
class StockFreshnessPolicyTests {

    private final StockFreshnessPolicy policy = new StockFreshnessPolicy();

    @Test
    void quoteWhileOpenIsCurrentWithinContractedDelayPlusThirtySeconds() {
        Instant observedAt = Instant.parse("2026-08-17T07:00:00Z");
        Duration contractedDelay = Duration.ofSeconds(15);
        assertThat(policy.evaluateQuoteWhileOpen(
                observedAt, observedAt.plus(contractedDelay).plusSeconds(30), contractedDelay))
                .isEqualTo(CURRENT);
        assertThat(policy.evaluateQuoteWhileOpen(
                observedAt, observedAt.plus(contractedDelay).plusSeconds(31), contractedDelay))
                .isEqualTo(DELAYED);
    }

    @Test
    void quoteWhileOpenIsStaleBeyondFifteenMinutesPastContractedDelay() {
        Instant observedAt = Instant.parse("2026-08-17T07:00:00Z");
        Duration contractedDelay = Duration.ZERO;
        assertThat(policy.evaluateQuoteWhileOpen(
                observedAt, observedAt.plus(Duration.ofMinutes(15)), contractedDelay))
                .isEqualTo(DELAYED);
        assertThat(policy.evaluateQuoteWhileOpen(
                observedAt, observedAt.plus(Duration.ofMinutes(15)).plusSeconds(1), contractedDelay))
                .isEqualTo(STALE);
    }

    @Test
    void quoteWhileClosedDegradesByCompletedSessionsBehind() {
        assertThat(policy.evaluateQuoteWhileClosed(0)).isEqualTo(CURRENT);
        assertThat(policy.evaluateQuoteWhileClosed(1)).isEqualTo(DELAYED);
        assertThat(policy.evaluateQuoteWhileClosed(2)).isEqualTo(STALE);
    }

    @Test
    void dailyBarSeriesDegradesByMissingTrailingSessions() {
        assertThat(policy.evaluateDailyBarSeries(0)).isEqualTo(CURRENT);
        assertThat(policy.evaluateDailyBarSeries(1)).isEqualTo(DELAYED);
        assertThat(policy.evaluateDailyBarSeries(2)).isEqualTo(STALE);
    }

    @Test
    void fundamentalReportIsCurrentWithinOneHundredNinetyDays() {
        LocalDate evaluationDate = LocalDate.of(2026, 8, 17);
        assertThat(policy.evaluateFundamentalReport(evaluationDate.minusDays(190), evaluationDate))
                .isEqualTo(CURRENT);
        assertThat(policy.evaluateFundamentalReport(evaluationDate.minusDays(191), evaluationDate))
                .isEqualTo(DELAYED);
    }

    @Test
    void fundamentalReportIsStaleBeyondTwoHundredEightyDays() {
        LocalDate evaluationDate = LocalDate.of(2026, 8, 17);
        assertThat(policy.evaluateFundamentalReport(evaluationDate.minusDays(280), evaluationDate))
                .isEqualTo(DELAYED);
        assertThat(policy.evaluateFundamentalReport(evaluationDate.minusDays(281), evaluationDate))
                .isEqualTo(STALE);
    }

    @Test
    void valuationAssessmentInheritsTheWorstInputState() {
        assertThat(policy.evaluateValuationAssessment(List.of(CURRENT, DELAYED, CURRENT))).isEqualTo(DELAYED);
        assertThat(policy.evaluateValuationAssessment(List.of(CURRENT, STALE))).isEqualTo(STALE);
        assertThat(policy.evaluateValuationAssessment(List.of())).isEqualTo(UNAVAILABLE);
    }

    @Test
    void missingDatasetIsUnavailable() {
        assertThat(policy.evaluateMissing()).isEqualTo(UNAVAILABLE);
    }

    @Test
    void rejectsNegativeContractedDelayAndEvaluatedAtBeforeObservedAt() {
        Instant observedAt = Instant.parse("2026-08-17T07:00:00Z");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> policy.evaluateQuoteWhileOpen(
                        observedAt, observedAt.minusSeconds(1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> policy.evaluateQuoteWhileOpen(
                        observedAt, observedAt, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
