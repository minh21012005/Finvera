package com.minhnb.finvera_be.stock.domain.fundamentals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * FR-007, FR-014, DATA-006, DATA-007.
 * Tests that {@link FundamentalReportAcceptance} enforces the period-identity contract,
 * handles restatement chains, correctly applies three-state applicability, and normalizes
 * unit-scale values (Vietnamese statements are often in millions/billions of VND).
 *
 * <p>These tests run red before {@code FundamentalReportAcceptance} exists.
 */
class FundamentalReportTests {

    private static final String CATALOG_V1 = "fundamental-metric-catalog-v1";

    // ── Period identity ─────────────────────────────────────────────────────────

    @Test
    void quarterReportWithNullFiscalQuarterIsRejected() {
        var acceptance = new FundamentalReportAcceptance();
        var input = new FundamentalReportAcceptance.ReportInput(
                "QUARTER", 2026, null,   // fiscal_quarter must be non-null for QUARTER
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                "CONSOLIDATED", "REVIEWED", "VND", 1, CATALOG_V1,
                List.of());
        assertThat(acceptance.accept(input).reasonCode()).isEqualTo("INVALID_PERIOD");
    }

    @Test
    void annualReportWithNonNullFiscalQuarterIsRejected() {
        var acceptance = new FundamentalReportAcceptance();
        var input = new FundamentalReportAcceptance.ReportInput(
                "ANNUAL", 2025, 4,       // fiscal_quarter must be null for ANNUAL
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                "CONSOLIDATED", "AUDITED", "VND", 1, CATALOG_V1,
                List.of());
        assertThat(acceptance.accept(input).reasonCode()).isEqualTo("INVALID_PERIOD");
    }

    @Test
    void reportWithPeriodEndBeforePeriodStartIsRejected() {
        var acceptance = new FundamentalReportAcceptance();
        var input = new FundamentalReportAcceptance.ReportInput(
                "QUARTER", 2026, 2,
                LocalDate.of(2026, 6, 30), LocalDate.of(2026, 4, 1), // period_end before period_start
                "CONSOLIDATED", "REVIEWED", "VND", 1, CATALOG_V1,
                List.of());
        assertThat(acceptance.accept(input).reasonCode()).isEqualTo("INVALID_PERIOD");
    }

    @Test
    void validQuarterReportIsAccepted() {
        var acceptance = new FundamentalReportAcceptance();
        var metrics = List.of(
                new FundamentalReportAcceptance.MetricInput("REVENUE",
                        new BigDecimal("16250000000000.000000"), "DEFINED", null));
        var input = new FundamentalReportAcceptance.ReportInput(
                "QUARTER", 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                "CONSOLIDATED", "REVIEWED", "VND", 1, CATALOG_V1,
                metrics);
        var result = acceptance.accept(input);
        assertThat(result.accepted()).isTrue();
        assertThat(result.reasonCode()).isNull();
    }

    // ── Three-state applicability ────────────────────────────────────────────────

    @Test
    void definedMetricCarriesItsValue() {
        var acceptance = new FundamentalReportAcceptance();
        var metrics = List.of(
                new FundamentalReportAcceptance.MetricInput("EPS",
                        new BigDecimal("1675.500000"), "DEFINED", null));
        var input = quarterInput(metrics);
        var result = acceptance.accept(input);

        assertThat(result.accepted()).isTrue();
        var eps = findMetric(result, "EPS");
        assertThat(eps.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(eps.value()).isEqualByComparingTo(new BigDecimal("1675.500000"));
    }

    @Test
    void notApplicableMetricHasNullValue() {
        var acceptance = new FundamentalReportAcceptance();
        var metrics = List.of(
                new FundamentalReportAcceptance.MetricInput("DIVIDEND_PER_SHARE",
                        null, "NOT_APPLICABLE", "NO_DIVIDEND_HISTORY"));
        var input = quarterInput(metrics);
        var result = acceptance.accept(input);

        assertThat(result.accepted()).isTrue();
        var dividend = findMetric(result, "DIVIDEND_PER_SHARE");
        assertThat(dividend.applicability()).isEqualTo(MetricApplicability.NOT_APPLICABLE);
        assertThat(dividend.value()).isNull();
        assertThat(dividend.qualityReason()).isEqualTo("NO_DIVIDEND_HISTORY");
    }

    @Test
    void definedMetricWithNullValueIsRejected() {
        // DATA-007: DEFINED must carry a value; null + DEFINED is a malformed input
        var acceptance = new FundamentalReportAcceptance();
        var metrics = List.of(
                new FundamentalReportAcceptance.MetricInput("REVENUE",
                        null, "DEFINED", null)); // value=null but applicability=DEFINED → invalid
        var input = quarterInput(metrics);
        var result = acceptance.accept(input);
        assertThat(result.reasonCode()).isEqualTo("INVALID_METRIC");
    }

    @Test
    void notApplicableMetricWithNonNullValueIsRejected() {
        // DATA-007: NOT_APPLICABLE must not carry a value
        var acceptance = new FundamentalReportAcceptance();
        var metrics = List.of(
                new FundamentalReportAcceptance.MetricInput("DIVIDEND_PER_SHARE",
                        new BigDecimal("1000.000000"), "NOT_APPLICABLE", null));
        var input = quarterInput(metrics);
        var result = acceptance.accept(input);
        assertThat(result.reasonCode()).isEqualTo("INVALID_METRIC");
    }

    @Test
    void unknownMetricCodeIsDroppedAndCounted() {
        // Unmapped line items are dropped and counted, never guessed (T047 spec)
        var acceptance = new FundamentalReportAcceptance();
        var metrics = List.of(
                new FundamentalReportAcceptance.MetricInput("REVENUE",
                        new BigDecimal("16250000000000.000000"), "DEFINED", null),
                new FundamentalReportAcceptance.MetricInput("MYSTERY_FIELD_FROM_PROVIDER",
                        new BigDecimal("99999.000000"), "DEFINED", null)); // unknown code
        var input = quarterInput(metrics);
        var result = acceptance.accept(input);
        // acceptance succeeds for the known metric, unknown is dropped
        assertThat(result.accepted()).isTrue();
        assertThat(result.droppedMetricCount()).isGreaterThan(0);
        // Unknown code must not appear in accepted metrics
        assertThat(result.metrics().stream().noneMatch(m -> "MYSTERY_FIELD_FROM_PROVIDER".equals(m.metricCode()))).isTrue();
    }

    // ── Unit-scale normalization ────────────────────────────────────────────────

    @Test
    void unitScaleMillionNormalizesRevenue() {
        // Vietnamese statements often published in millions of VND (unitScale=1_000_000)
        var acceptance = new FundamentalReportAcceptance();
        BigDecimal rawRevenue = new BigDecimal("16250000.000000");   // 16,250,000 in millions = 16.25T VND
        var metrics = List.of(
                new FundamentalReportAcceptance.MetricInput("REVENUE", rawRevenue, "DEFINED", null));
        var input = new FundamentalReportAcceptance.ReportInput(
                "QUARTER", 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                "CONSOLIDATED", "REVIEWED", "VND", 1_000_000, CATALOG_V1,
                metrics);
        var result = acceptance.accept(input);
        assertThat(result.accepted()).isTrue();
        var revenue = findMetric(result, "REVENUE");
        // stored value is normalized to base VND: 16,250,000 * 1,000,000 = 16,250,000,000,000
        assertThat(revenue.value()).isEqualByComparingTo(new BigDecimal("16250000000000.000000"));
    }

    @Test
    void unitScaleOneKeepsValueUnchanged() {
        var acceptance = new FundamentalReportAcceptance();
        BigDecimal raw = new BigDecimal("1675.500000");
        var metrics = List.of(
                new FundamentalReportAcceptance.MetricInput("EPS", raw, "DEFINED", null));
        var input = quarterInput(metrics);
        var result = acceptance.accept(input);
        assertThat(result.accepted()).isTrue();
        assertThat(findMetric(result, "EPS").value()).isEqualByComparingTo(raw);
    }

    // ── Restatement identity ────────────────────────────────────────────────────

    @Test
    void periodIdentityKeyIncludesPeriodTypeYearQuarterKind() {
        var acceptance = new FundamentalReportAcceptance();
        var q1 = quarterInput("CONSOLIDATED", 2026, 1, List.of());
        var q2 = quarterInput("CONSOLIDATED", 2026, 2, List.of());
        // same period-type+year+quarter+kind must share the same identity
        assertThat(acceptance.periodIdentityKey(q1))
                .isNotEqualTo(acceptance.periodIdentityKey(q2));
        // separate and consolidated are distinct
        var q1sep = quarterInput("SEPARATE", 2026, 1, List.of());
        assertThat(acceptance.periodIdentityKey(q1))
                .isNotEqualTo(acceptance.periodIdentityKey(q1sep));
    }

    // ── Helper factories ────────────────────────────────────────────────────────

    private static FundamentalReportAcceptance.ReportInput quarterInput(
            List<FundamentalReportAcceptance.MetricInput> metrics) {
        return quarterInput("CONSOLIDATED", 2026, 2, metrics);
    }

    private static FundamentalReportAcceptance.ReportInput quarterInput(
            String reportKind, int fiscalYear, int fiscalQuarter,
            List<FundamentalReportAcceptance.MetricInput> metrics) {
        return new FundamentalReportAcceptance.ReportInput(
                "QUARTER", fiscalYear, fiscalQuarter,
                LocalDate.of(fiscalYear, fiscalQuarter * 3 - 2, 1),
                LocalDate.of(fiscalYear, fiscalQuarter * 3, 28),
                reportKind, "REVIEWED", "VND", 1, CATALOG_V1,
                metrics);
    }

    private static FundamentalReportAcceptance.AcceptedMetric findMetric(
            FundamentalReportAcceptance.AcceptanceResult result, String metricCode) {
        return result.metrics().stream()
                .filter(m -> m.metricCode().equals(metricCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Metric not found: " + metricCode));
    }
}
