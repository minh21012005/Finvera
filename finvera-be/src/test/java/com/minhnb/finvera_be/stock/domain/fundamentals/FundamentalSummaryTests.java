package com.minhnb.finvera_be.stock.domain.fundamentals;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * FR-007, DATA-009.
 * Tests that {@link FundamentalSummaryCalculator} produces correct TTM aggregates,
 * EPS growth, and correctly links contributing report IDs.
 * A restatement of any contributing report must invalidate and relink the summary.
 *
 * <p>These tests run red before {@code FundamentalSummaryCalculator} exists.
 */
class FundamentalSummaryTests {

    private static final String RULE_VERSION = FundamentalSummaryCalculator.RULE_VERSION;

    // ── TTM from four quarters ─────────────────────────────────────────────────

    @Test
    void ttmNetProfitIsTheSumOfFourMostRecentQuarters() {
        // Given four quarterly NET_PROFIT values, TTM should be their sum.
        // Q3-2025: 1000, Q4-2025: 1200, Q1-2026: 900, Q2-2026: 1100
        // TTM = 4200 (all values in base VND units)
        var calculator = new FundamentalSummaryCalculator();
        var q3 = quarterReport(UUID.randomUUID(), 2025, 3,
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 9, 30),
                metric("NET_PROFIT", "1000.000000"));
        var q4 = quarterReport(UUID.randomUUID(), 2025, 4,
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31),
                metric("NET_PROFIT", "1200.000000"));
        var q1 = quarterReport(UUID.randomUUID(), 2026, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                metric("NET_PROFIT", "900.000000"));
        var q2 = quarterReport(UUID.randomUUID(), 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("NET_PROFIT", "1100.000000"));

        var result = calculator.calculate(List.of(q3, q4, q1, q2), LocalDate.of(2026, 8, 14));

        assertThat(result.ruleVersion()).isEqualTo(RULE_VERSION);
        var ttmNetProfit = findSummaryMetric(result, "NET_PROFIT_TTM");
        assertThat(ttmNetProfit.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(ttmNetProfit.value()).isEqualByComparingTo(new BigDecimal("4200.000000"));
    }

    @Test
    void ttmEpsIsTheSumOfFourMostRecentQuarterEps() {
        var calculator = new FundamentalSummaryCalculator();
        // EPS is a per-share value (already annualized implicitly by summing four quarters)
        var q3 = quarterReport(UUID.randomUUID(), 2025, 3,
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 9, 30),
                metric("EPS", "980.000000"));
        var q4 = quarterReport(UUID.randomUUID(), 2025, 4,
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31),
                metric("EPS", "1100.000000"));
        var q1 = quarterReport(UUID.randomUUID(), 2026, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                metric("EPS", "1200.000000"));
        var q2 = quarterReport(UUID.randomUUID(), 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("EPS", "1300.000000"));

        var result = calculator.calculate(List.of(q3, q4, q1, q2), LocalDate.of(2026, 8, 14));

        var epsTtm = findSummaryMetric(result, "EPS_TTM");
        assertThat(epsTtm.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(epsTtm.value()).isEqualByComparingTo(new BigDecimal("4580.000000"));
    }

    @Test
    void dividendPerShareTtmIsSumOfFourQuarters() {
        var calculator = new FundamentalSummaryCalculator();
        var q3 = quarterReport(UUID.randomUUID(), 2025, 3,
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 9, 30),
                metric("DIVIDEND_PER_SHARE", "250.000000"));
        var q4 = quarterReport(UUID.randomUUID(), 2025, 4,
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31),
                metric("DIVIDEND_PER_SHARE", "250.000000"));
        var q1 = quarterReport(UUID.randomUUID(), 2026, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                metric("DIVIDEND_PER_SHARE", "250.000000"));
        var q2 = quarterReport(UUID.randomUUID(), 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("DIVIDEND_PER_SHARE", "250.000000"));

        var result = calculator.calculate(List.of(q3, q4, q1, q2), LocalDate.of(2026, 8, 14));

        var dividendTtm = findSummaryMetric(result, "DIVIDEND_PER_SHARE_TTM");
        assertThat(dividendTtm.value()).isEqualByComparingTo(new BigDecimal("1000.000000"));
    }

    // ── Latest-period scalar metrics ─────────────────────────────────────────────

    @Test
    void roeBorrowsFromNewestPeriodDirectly() {
        // ROE, ROA, DEBT_TO_EQUITY, OPERATING_MARGIN from the most recent report
        var calculator = new FundamentalSummaryCalculator();
        var q2 = quarterReport(UUID.randomUUID(), 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("ROE", "6.850000"));

        var result = calculator.calculate(List.of(q2), LocalDate.of(2026, 8, 14));

        var roe = findSummaryMetric(result, "ROE");
        assertThat(roe.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(roe.value()).isEqualByComparingTo(new BigDecimal("6.850000"));
    }

    @Test
    void bvpsAndDividendYieldBorrowFromNewestPeriodDirectly() {
        var calculator = new FundamentalSummaryCalculator();
        var q2 = quarterReport(UUID.randomUUID(), 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("BVPS", "18160.000000"),
                metric("DIVIDEND_YIELD", "0.040000"));

        var result = calculator.calculate(List.of(q2), LocalDate.of(2026, 8, 14));

        var bvps = findSummaryMetric(result, "BVPS");
        assertThat(bvps.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(bvps.value()).isEqualByComparingTo(new BigDecimal("18160.000000"));

        var divYield = findSummaryMetric(result, "DIVIDEND_YIELD");
        assertThat(divYield.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(divYield.value()).isEqualByComparingTo(new BigDecimal("0.040000"));
    }

    @Test
    void trailingEpsFallbackUsedWhenQuarterlyEpsMissing() {
        var calculator = new FundamentalSummaryCalculator();
        // A single quarter report without quarterly EPS, but carrying TRAILING_EPS (financial stock shape)
        var q2 = quarterReport(UUID.randomUUID(), 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("TRAILING_EPS", "4050.730000"));

        var result = calculator.calculate(List.of(q2), LocalDate.of(2026, 8, 14));

        var epsTtm = findSummaryMetric(result, "EPS_TTM");
        assertThat(epsTtm.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(epsTtm.value()).isEqualByComparingTo(new BigDecimal("4050.730000"));
    }

    // ── EPS year-over-year growth ────────────────────────────────────────────────

    @Test
    void epsGrowthPercentIsTtmOverPriorTtmMinus100() {
        // epsGrowthPercent = (currentTtmEps / priorTtmEps - 1) * 100
        // prior TTM EPS = 980 + 1000 + 1050 + 1100 = 4130
        // current TTM EPS = 1200 + 1250 + 1300 + 1350 = 5100
        // growth = (5100/4130 - 1) * 100 = 23.4866... %
        var calculator = new FundamentalSummaryCalculator();

        // Prior year quarters (for the prior TTM window)
        var q3_2024 = quarterReport(UUID.randomUUID(), 2024, 3,
                LocalDate.of(2024, 7, 1), LocalDate.of(2024, 9, 30),
                metric("EPS", "980.000000"));
        var q4_2024 = quarterReport(UUID.randomUUID(), 2024, 4,
                LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31),
                metric("EPS", "1000.000000"));
        var q1_2025 = quarterReport(UUID.randomUUID(), 2025, 1,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31),
                metric("EPS", "1050.000000"));
        var q2_2025 = quarterReport(UUID.randomUUID(), 2025, 2,
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 6, 30),
                metric("EPS", "1100.000000"));
        // Current year quarters
        var q3_2025 = quarterReport(UUID.randomUUID(), 2025, 3,
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 9, 30),
                metric("EPS", "1200.000000"));
        var q4_2025 = quarterReport(UUID.randomUUID(), 2025, 4,
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31),
                metric("EPS", "1250.000000"));
        var q1_2026 = quarterReport(UUID.randomUUID(), 2026, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                metric("EPS", "1300.000000"));
        var q2_2026 = quarterReport(UUID.randomUUID(), 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("EPS", "1350.000000"));

        var result = calculator.calculate(
                List.of(q3_2024, q4_2024, q1_2025, q2_2025, q3_2025, q4_2025, q1_2026, q2_2026),
                LocalDate.of(2026, 8, 14));

        var growth = findSummaryMetric(result, "EPS_GROWTH_PERCENT");
        assertThat(growth.applicability()).isEqualTo(MetricApplicability.DEFINED);
        // priorTtm = 980+1000+1050+1100 = 4130; currentTtm = 1200+1250+1300+1350 = 5100
        // growth = (5100/4130 - 1) * 100 = 23.486...
        assertThat(growth.value().doubleValue()).isCloseTo(23.4866, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void epsGrowthIsNotApplicableWhenPriorTtmEpsIsZeroOrNegative() {
        var calculator = new FundamentalSummaryCalculator();
        // prior year EPS = -100 (loss-making) → growth is NOT_APPLICABLE
        var priorQuarters = List.of(
                quarterReport(UUID.randomUUID(), 2024, 3,
                        LocalDate.of(2024, 7, 1), LocalDate.of(2024, 9, 30),
                        metric("EPS", "-100.000000")),
                quarterReport(UUID.randomUUID(), 2024, 4,
                        LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31),
                        metric("EPS", "-50.000000")),
                quarterReport(UUID.randomUUID(), 2025, 1,
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31),
                        metric("EPS", "20.000000")),
                quarterReport(UUID.randomUUID(), 2025, 2,
                        LocalDate.of(2025, 4, 1), LocalDate.of(2025, 6, 30),
                        metric("EPS", "30.000000")),
                quarterReport(UUID.randomUUID(), 2025, 3,
                        LocalDate.of(2025, 7, 1), LocalDate.of(2025, 9, 30),
                        metric("EPS", "500.000000")),
                quarterReport(UUID.randomUUID(), 2025, 4,
                        LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31),
                        metric("EPS", "600.000000")),
                quarterReport(UUID.randomUUID(), 2026, 1,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                        metric("EPS", "700.000000")),
                quarterReport(UUID.randomUUID(), 2026, 2,
                        LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                        metric("EPS", "800.000000"))
        );
        var result = calculator.calculate(priorQuarters, LocalDate.of(2026, 8, 14));
        var growth = findSummaryMetric(result, "EPS_GROWTH_PERCENT");
        // priorTtm = -100 + -50 + 20 + 30 = -100 → growth NOT_APPLICABLE
        assertThat(growth.applicability()).isEqualTo(MetricApplicability.NOT_APPLICABLE);
    }

    // ── Revenue year-over-year growth (Feature 003 research R-005) ──────────────

    @Test
    void revenueGrowthPercentIsTtmOverPriorTtmMinus100() {
        // Mirrors epsGrowthPercentIsTtmOverPriorTtmMinus100 exactly, on REVENUE.
        // priorTtm = 980+1000+1050+1100 = 4130; currentTtm = 1200+1250+1300+1350 = 5100
        // growth = (5100/4130 - 1) * 100 = 23.4866...%
        var calculator = new FundamentalSummaryCalculator();

        var q3_2024 = quarterReport(UUID.randomUUID(), 2024, 3,
                LocalDate.of(2024, 7, 1), LocalDate.of(2024, 9, 30),
                metric("REVENUE", "980.000000"));
        var q4_2024 = quarterReport(UUID.randomUUID(), 2024, 4,
                LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31),
                metric("REVENUE", "1000.000000"));
        var q1_2025 = quarterReport(UUID.randomUUID(), 2025, 1,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31),
                metric("REVENUE", "1050.000000"));
        var q2_2025 = quarterReport(UUID.randomUUID(), 2025, 2,
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 6, 30),
                metric("REVENUE", "1100.000000"));
        var q3_2025 = quarterReport(UUID.randomUUID(), 2025, 3,
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 9, 30),
                metric("REVENUE", "1200.000000"));
        var q4_2025 = quarterReport(UUID.randomUUID(), 2025, 4,
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31),
                metric("REVENUE", "1250.000000"));
        var q1_2026 = quarterReport(UUID.randomUUID(), 2026, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                metric("REVENUE", "1300.000000"));
        var q2_2026 = quarterReport(UUID.randomUUID(), 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("REVENUE", "1350.000000"));

        var result = calculator.calculate(
                List.of(q3_2024, q4_2024, q1_2025, q2_2025, q3_2025, q4_2025, q1_2026, q2_2026),
                LocalDate.of(2026, 8, 14));

        var growth = findSummaryMetric(result, "REVENUE_GROWTH_PERCENT");
        assertThat(growth.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(growth.value().doubleValue()).isCloseTo(23.4866, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void revenueGrowthIsNotApplicableWhenPriorTtmRevenueIsZeroOrNegative() {
        var calculator = new FundamentalSummaryCalculator();
        var priorQuarters = List.of(
                quarterReport(UUID.randomUUID(), 2024, 3,
                        LocalDate.of(2024, 7, 1), LocalDate.of(2024, 9, 30),
                        metric("REVENUE", "-100.000000")),
                quarterReport(UUID.randomUUID(), 2024, 4,
                        LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31),
                        metric("REVENUE", "-50.000000")),
                quarterReport(UUID.randomUUID(), 2025, 1,
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31),
                        metric("REVENUE", "20.000000")),
                quarterReport(UUID.randomUUID(), 2025, 2,
                        LocalDate.of(2025, 4, 1), LocalDate.of(2025, 6, 30),
                        metric("REVENUE", "30.000000")),
                quarterReport(UUID.randomUUID(), 2025, 3,
                        LocalDate.of(2025, 7, 1), LocalDate.of(2025, 9, 30),
                        metric("REVENUE", "500.000000")),
                quarterReport(UUID.randomUUID(), 2025, 4,
                        LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31),
                        metric("REVENUE", "600.000000")),
                quarterReport(UUID.randomUUID(), 2026, 1,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                        metric("REVENUE", "700.000000")),
                quarterReport(UUID.randomUUID(), 2026, 2,
                        LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                        metric("REVENUE", "800.000000"))
        );
        var result = calculator.calculate(priorQuarters, LocalDate.of(2026, 8, 14));
        var growth = findSummaryMetric(result, "REVENUE_GROWTH_PERCENT");
        // priorTtm = -100 + -50 + 20 + 30 = -100 → growth NOT_APPLICABLE
        assertThat(growth.applicability()).isEqualTo(MetricApplicability.NOT_APPLICABLE);
    }

    @Test
    void revenueGrowthIsMissingWhenFewerThanEightQuartersExist() {
        var calculator = new FundamentalSummaryCalculator();
        var q2 = quarterReport(UUID.randomUUID(), 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("REVENUE", "1300.000000"));

        var result = calculator.calculate(List.of(q2), LocalDate.of(2026, 8, 14));

        var growth = findSummaryMetric(result, "REVENUE_GROWTH_PERCENT");
        assertThat(growth.applicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(growth.qualityReason()).isEqualTo("INSUFFICIENT_HISTORY");
    }

    @Test
    void revenueGrowthDoesNotChangeExistingEpsGrowthValue() {
        // Guards against the refactor (research R-005) accidentally coupling
        // the two growth metrics together.
        var calculator = new FundamentalSummaryCalculator();
        var q3_2024 = quarterReport(UUID.randomUUID(), 2024, 3,
                LocalDate.of(2024, 7, 1), LocalDate.of(2024, 9, 30),
                metric("EPS", "980.000000"), metric("REVENUE", "5000.000000"));
        var q4_2024 = quarterReport(UUID.randomUUID(), 2024, 4,
                LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31),
                metric("EPS", "1000.000000"), metric("REVENUE", "5000.000000"));
        var q1_2025 = quarterReport(UUID.randomUUID(), 2025, 1,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31),
                metric("EPS", "1050.000000"), metric("REVENUE", "5000.000000"));
        var q2_2025 = quarterReport(UUID.randomUUID(), 2025, 2,
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 6, 30),
                metric("EPS", "1100.000000"), metric("REVENUE", "5000.000000"));
        var q3_2025 = quarterReport(UUID.randomUUID(), 2025, 3,
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 9, 30),
                metric("EPS", "1200.000000"), metric("REVENUE", "9000.000000"));
        var q4_2025 = quarterReport(UUID.randomUUID(), 2025, 4,
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31),
                metric("EPS", "1250.000000"), metric("REVENUE", "9000.000000"));
        var q1_2026 = quarterReport(UUID.randomUUID(), 2026, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                metric("EPS", "1300.000000"), metric("REVENUE", "9000.000000"));
        var q2_2026 = quarterReport(UUID.randomUUID(), 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("EPS", "1350.000000"), metric("REVENUE", "9000.000000"));

        var result = calculator.calculate(
                List.of(q3_2024, q4_2024, q1_2025, q2_2025, q3_2025, q4_2025, q1_2026, q2_2026),
                LocalDate.of(2026, 8, 14));

        var epsGrowth = findSummaryMetric(result, "EPS_GROWTH_PERCENT");
        assertThat(epsGrowth.value().doubleValue()).isCloseTo(23.4866, org.assertj.core.data.Offset.offset(0.001));
        var revenueGrowth = findSummaryMetric(result, "REVENUE_GROWTH_PERCENT");
        // priorTtm = 4*5000=20000, currentTtm = 4*9000=36000 -> growth = (36000/20000-1)*100 = 80
        assertThat(revenueGrowth.value()).isEqualByComparingTo(new BigDecimal("80"));
    }

    // ── Input linkage ─────────────────────────────────────────────────────────────

    @Test
    void contributingReportIdsAreLinkedInResult() {
        var calculator = new FundamentalSummaryCalculator();
        UUID reportId = UUID.randomUUID();
        var q2 = quarterReport(reportId, 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("EPS", "1300.000000"));

        var result = calculator.calculate(List.of(q2), LocalDate.of(2026, 8, 14));

        // The contributing report IDs must be explicitly linked for restatement traceability
        assertThat(result.contributingReportIds()).contains(reportId);
    }

    @Test
    void basisPeriodLabelReflectsNewestContributingPeriod() {
        var calculator = new FundamentalSummaryCalculator();
        var q2 = quarterReport(UUID.randomUUID(), 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("EPS", "1300.000000"));
        var result = calculator.calculate(List.of(q2), LocalDate.of(2026, 8, 14));
        // period label should be "2026-Q2" for fiscal year 2026 quarter 2
        assertThat(result.basisPeriodLabel()).isEqualTo("2026-Q2");
    }

    @Test
    void basisPeriodEndIsTheNewestPeriodEnd() {
        var calculator = new FundamentalSummaryCalculator();
        var q1 = quarterReport(UUID.randomUUID(), 2026, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                metric("EPS", "1200.000000"));
        var q2 = quarterReport(UUID.randomUUID(), 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("EPS", "1300.000000"));
        var result = calculator.calculate(List.of(q1, q2), LocalDate.of(2026, 8, 14));
        assertThat(result.basisPeriodEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    // ── Data status: stale fundamentals ─────────────────────────────────────────

    @Test
    void reportWith300DayOldPeriodEndProducesStaleDataStatus() {
        // R-010: beyond 280 days is STALE
        var calculator = new FundamentalSummaryCalculator();
        LocalDate asOfDate = LocalDate.of(2026, 8, 17);
        LocalDate stalePeriodEnd = LocalDate.of(2025, 9, 30); // 301 days before asOf
        var staleQ = quarterReport(UUID.randomUUID(), 2025, 3,
                LocalDate.of(2025, 7, 1), stalePeriodEnd,
                metric("EPS", "980.000000"));

        var result = calculator.calculate(List.of(staleQ), asOfDate);

        assertThat(result.dataStatus()).isEqualTo(DataStatus.STALE);
        assertThat(result.reasonCodes()).contains("FUNDAMENTALS_STALE");
    }

    @Test
    void reportWith200DayOldPeriodEndProducesCurrentDataStatus() {
        // 200 days < 190-day DELAYED threshold needs checking:
        // R-010: CURRENT while within 190 days, DELAYED 190-280 days, STALE beyond 280
        var calculator = new FundamentalSummaryCalculator();
        LocalDate asOfDate = LocalDate.of(2026, 8, 17);
        LocalDate currentPeriodEnd = asOfDate.minusDays(100); // well within 190d threshold
        var recentQ = quarterReport(UUID.randomUUID(), 2026, 2,
                currentPeriodEnd.minusDays(90), currentPeriodEnd,
                metric("EPS", "1300.000000"));

        var result = calculator.calculate(List.of(recentQ), asOfDate);

        assertThat(result.dataStatus()).isEqualTo(DataStatus.CURRENT);
    }

    // ── Missing metric when absent from all quarters ──────────────────────────────

    @Test
    void ttmMetricIsMissingWhenNoQuarterHasIt() {
        var calculator = new FundamentalSummaryCalculator();
        // Reports only have EPS, no REVENUE
        var q2 = quarterReport(UUID.randomUUID(), 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                metric("EPS", "1300.000000"));
        var result = calculator.calculate(List.of(q2), LocalDate.of(2026, 8, 14));
        // NET_PROFIT_TTM should be MISSING since it's not in any report
        var netProfit = result.metrics().stream()
                .filter(m -> "NET_PROFIT_TTM".equals(m.metricCode()))
                .findFirst();
        // Either absent or MISSING, never a fabricated zero
        netProfit.ifPresent(m -> assertThat(m.applicability()).isEqualTo(MetricApplicability.MISSING));
    }

    // ── Helper factories ────────────────────────────────────────────────────────

    private static FundamentalSummaryCalculator.ReportPeriod quarterReport(
            UUID reportId, int fiscalYear, int fiscalQuarter,
            LocalDate periodStart, LocalDate periodEnd,
            FundamentalSummaryCalculator.ReportMetric... metrics) {
        return new FundamentalSummaryCalculator.ReportPeriod(
                reportId, "QUARTER", fiscalYear, fiscalQuarter,
                periodStart, periodEnd, "CONSOLIDATED", List.of(metrics));
    }

    private static FundamentalSummaryCalculator.ReportMetric metric(String code, String value) {
        return new FundamentalSummaryCalculator.ReportMetric(code,
                new BigDecimal(value), MetricApplicability.DEFINED, null);
    }

    private static FundamentalSummaryCalculator.SummaryMetric findSummaryMetric(
            FundamentalSummaryCalculator.SummaryResult result, String metricCode) {
        return result.metrics().stream()
                .filter(m -> m.metricCode().equals(metricCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Summary metric not found: " + metricCode));
    }

    @Test
    void freeCashFlowFallsBackToTheLatestAnnualReportWhenTheNewestQuarterLacksIt() {
        var calculator = new FundamentalSummaryCalculator();
        var q2 = new FundamentalSummaryCalculator.ReportPeriod(java.util.UUID.randomUUID(), "QUARTER", 2026, 2,
                java.time.LocalDate.of(2026, 4, 1), java.time.LocalDate.of(2026, 6, 30), "UNKNOWN",
                java.util.List.of(new FundamentalSummaryCalculator.ReportMetric("REVENUE",
                        new java.math.BigDecimal("16968084098000"), MetricApplicability.DEFINED, null)));
        var fy2025 = new FundamentalSummaryCalculator.ReportPeriod(java.util.UUID.randomUUID(), "ANNUAL", 2025, null,
                java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2025, 12, 31), "UNKNOWN",
                java.util.List.of(new FundamentalSummaryCalculator.ReportMetric("FREE_CASH_FLOW",
                        new java.math.BigDecimal("7370359124000"), MetricApplicability.DEFINED, "kbs-fcf-ocf-plus-capex-v1")));

        var result = calculator.calculate(java.util.List.of(q2, fy2025), java.time.LocalDate.of(2026, 8, 30));

        var fcf = result.metrics().stream().filter(m -> m.metricCode().equals("FREE_CASH_FLOW")).findFirst().orElseThrow();
        assertThat(fcf.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(fcf.value()).isEqualByComparingTo("7370359124000");
        assertThat(fcf.qualityReason()).isEqualTo("kbs-fcf-ocf-plus-capex-v1");
        // The newest period is still the quarter; the annual fallback does not move the basis.
        assertThat(result.basisPeriodLabel()).isEqualTo("2026-Q2");
    }

    private static FundamentalSummaryCalculator.ReportPeriod annualReport(int fiscalYear,
            FundamentalSummaryCalculator.ReportMetric... metrics) {
        return new FundamentalSummaryCalculator.ReportPeriod(UUID.randomUUID(), "ANNUAL", fiscalYear, null,
                LocalDate.of(fiscalYear, 1, 1), LocalDate.of(fiscalYear, 12, 31), "CONSOLIDATED", List.of(metrics));
    }

    @Test
    void v2UsesTheLatestAnnualReportForTtmWhenFewerThanFourQuartersAreVisible() {
        var calculator = new FundamentalSummaryCalculator();
        var reports = List.of(
                quarterReport(UUID.randomUUID(), 2026, 1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), metric("EPS", "1000.000000")),
                quarterReport(UUID.randomUUID(), 2026, 2, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), metric("EPS", "1100.000000")),
                annualReport(2025, metric("EPS", "4159.650000"), metric("REVENUE", "62000.000000")),
                annualReport(2024, metric("EPS", "3800.000000"), metric("REVENUE", "60000.000000")));

        var result = calculator.calculate(reports, LocalDate.of(2026, 8, 30));

        var eps = findSummaryMetric(result, "EPS_TTM");
        assertThat(eps.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(eps.value()).isEqualByComparingTo("4159.65");   // annual, never 1000 + 1100
        assertThat(eps.qualityReason()).isEqualTo("ANNUAL_BASIS");
        assertThat(result.basisPeriodLabel()).isEqualTo("2026-Q2"); // basis period is still the newest quarter
        var growth = findSummaryMetric(result, "EPS_GROWTH_PERCENT");
        assertThat(growth.applicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(growth.value()).isEqualByComparingTo("9.464473684200"); // (4159.65/3800 - 1) * 100 at scale 12
        assertThat(growth.qualityReason()).isEqualTo("ANNUAL_BASIS");
        var revenueGrowth = findSummaryMetric(result, "REVENUE_GROWTH_PERCENT");
        assertThat(revenueGrowth.value()).isEqualByComparingTo("3.333333333300");
    }

    @Test
    void v2AnnualGrowthIsNotApplicableWhenThePriorYearIsNonPositive() {
        var calculator = new FundamentalSummaryCalculator();
        var reports = List.of(
                annualReport(2025, metric("EPS", "500.000000")),
                annualReport(2024, metric("EPS", "-20.000000")));
        var result = calculator.calculate(reports, LocalDate.of(2026, 8, 30));
        assertThat(findSummaryMetric(result, "EPS_GROWTH_PERCENT").applicability()).isEqualTo(MetricApplicability.NOT_APPLICABLE);
    }

    @Test
    void v2KeepsTheQuarterlyRuleWhenEightQuartersAreVisibleEvenIfAnnualsExist() {
        var calculator = new FundamentalSummaryCalculator();
        var reports = new java.util.ArrayList<FundamentalSummaryCalculator.ReportPeriod>();
        int[][] q = {{2024, 3}, {2024, 4}, {2025, 1}, {2025, 2}, {2025, 3}, {2025, 4}, {2026, 1}, {2026, 2}};
        String[] eps = {"100", "100", "100", "100", "150", "150", "150", "150"};
        for (int i = 0; i < q.length; i++) {
            int y = q[i][0], qq = q[i][1];
            reports.add(quarterReport(UUID.randomUUID(), y, qq, LocalDate.of(y, (qq - 1) * 3 + 1, 1),
                    LocalDate.of(y, qq * 3, 28), metric("EPS", eps[i] + ".000000")));
        }
        reports.add(annualReport(2025, metric("EPS", "9999.000000")));
        reports.add(annualReport(2024, metric("EPS", "1.000000")));
        var result = calculator.calculate(reports, LocalDate.of(2026, 8, 30));
        var growth = findSummaryMetric(result, "EPS_GROWTH_PERCENT");
        assertThat(growth.value()).isEqualByComparingTo("50"); // 600 vs 400, quarterly rule, not 9999 vs 1
        assertThat(growth.qualityReason()).isNull();
        assertThat(findSummaryMetric(result, "EPS_TTM").qualityReason()).isNull();
    }
}
