package com.minhnb.finvera_be.stock.domain.fundamentals;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.stock.domain.model.DecimalMath;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * FR-007, DATA-009, R-006, R-010.
 * Pure domain calculator for fundamental summary (TTM aggregates, growth, balance sheet snapshots).
 */
public final class FundamentalSummaryCalculator {

    /**
     * v2 (Feature 010 research R-005): when fewer than four quarters are visible the TTM
     * figures come from the latest annual report, and when fewer than eight quarters are
     * visible growth is annual-over-prior-annual; every such metric carries
     * {@code ANNUAL_BASIS}. Partial quarter sets are never mixed with annual figures.
     *
     * <p>v3 (Feature 025, contract fundamental-summary-v3, closes Q-60): a quarterly window is used
     * only when it is <em>eligible</em> — four (or eight) consecutive fiscal quarters that no annual
     * report already supersedes. See {@link #quarterWindowEligible}. v2 selected the window by
     * counting alone, which served 60 instruments a "TTM" summed from non-contiguous or years-old
     * quarters while a current annual report sat unused.
     */
    public static final String RULE_VERSION = "fundamental-summary-v3";
    public static final String ANNUAL_BASIS = "ANNUAL_BASIS";
    /**
     * v3 (Q-60): the newest quarterly reports did not form a real trailing window — they were not
     * consecutive fiscal quarters, or an annual report we already hold covers a later period. The
     * aggregates came from the annual report instead, or are withheld when there is none.
     */
    public static final String QUARTER_WINDOW_INELIGIBLE = "QUARTER_WINDOW_INELIGIBLE";
    /** Newest report first: period end desc, then QUARTER before ANNUAL, then report id (total order). */
    static final Comparator<ReportPeriod> NEWEST_FIRST = Comparator
            .comparing(ReportPeriod::periodEnd, Comparator.reverseOrder())
            .thenComparing(r -> "QUARTER".equals(r.periodType()) ? 0 : 1)
            .thenComparing(r -> r.reportId() == null ? "" : r.reportId().toString());
    /** EPS_TTM taken from the provider's own trailing EPS because quarterly EPS is not reported (banks, securities). */
    public static final String PROVIDER_TRAILING_EPS = "PROVIDER_TRAILING_EPS";
    /**
     * Feature 011 (contract provider-ratio-facts-v2 U-4): codes the provider only reports on an
     * annual basis. Read from the newest report when present, else from the latest annual report
     * and labelled {@code ANNUAL_BASIS}.
     */
    public static final Set<String> ANNUAL_ONLY_CODES = Set.of(
            "ROCE", "NIM", "TOTAL_ASSET_TURNOVER", "INVENTORY_TURNOVER", "RECEIVABLES_TURNOVER",
            "DIVIDEND_YIELD", "PS", "TOTAL_ASSETS_GROWTH_PERCENT", "EQUITY_GROWTH_PERCENT");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /**
     * Which reports feed the period aggregates (NET_PROFIT_TTM, EPS_TTM, REVENUE_TTM, EBITDA_TTM,
     * DIVIDEND_PER_SHARE_TTM) and the growth metrics.
     *
     * <ul>
     *   <li>{@link #PREFER_QUARTERS} — the four newest quarters when they form an ELIGIBLE window
     *       ({@link #quarterWindowEligible}, contract {@code fundamental-summary-v3}), else the latest
     *       annual report ({@code ANNUAL_BASIS}); growth from eight eligible quarters, else annual over
     *       prior annual. The persisted summary always uses this.</li>
     *   <li>{@link #FISCAL_YEAR} — contract {@code valuation-v3} (specs/023): the latest visible
     *       annual report regardless of how many quarters are visible, growth annual over prior
     *       annual, and no provider trailing-EPS fallback (a TTM figure). Used only to build the
     *       own-history series and its comparison value on one basis; snapshot metrics are
     *       unaffected.</li>
     * </ul>
     */
    public enum AggregateBasis { PREFER_QUARTERS, FISCAL_YEAR }

    /**
     * Contract {@code fundamental-summary-v3} (Q-60): a window of quarterly reports may only form a
     * period aggregate when it is a real twelve- (or twenty-four-) month span.
     *
     * <ul>
     *   <li><b>E-1 contiguity</b> — the quarter indices ({@code fiscalYear * 4 + fiscalQuarter}),
     *       newest first, decrease by exactly one at every step. Decided on indices rather than day
     *       spans because a company's fiscal quarters need not be calendar quarters (research
     *       R-003).</li>
     *   <li><b>E-2 not superseded</b> — no accepted annual report ends later than the newest
     *       quarter in the window. A fresher annual figure is never passed over for staler
     *       quarters.</li>
     * </ul>
     */
    private static boolean quarterWindowEligible(List<ReportPeriod> window, List<ReportPeriod> annualReports) {
        if (window.isEmpty()) {
            return false;
        }
        for (int i = 0; i < window.size() - 1; i++) {
            if (quarterIndex(window.get(i)) - quarterIndex(window.get(i + 1)) != 1) {
                return false; // E-1
            }
        }
        LocalDate newestQuarterEnd = window.get(0).periodEnd();
        for (ReportPeriod annual : annualReports) {
            if (annual.periodEnd() != null && newestQuarterEnd != null
                    && annual.periodEnd().isAfter(newestQuarterEnd)) {
                return false; // E-2
            }
        }
        return true;
    }

    private static int quarterIndex(ReportPeriod period) {
        Integer quarter = period.fiscalQuarter();
        // A QUARTER report without a fiscal quarter cannot be placed in a sequence; treat the
        // window as broken rather than guessing its position.
        return quarter == null ? Integer.MIN_VALUE / 4 : period.fiscalYear() * 4 + quarter;
    }

    public SummaryResult calculate(List<ReportPeriod> reports, LocalDate asOfDate) {
        return calculate(reports, asOfDate, AggregateBasis.PREFER_QUARTERS);
    }

    public SummaryResult calculate(List<ReportPeriod> reports, LocalDate asOfDate, AggregateBasis basis) {
        Objects.requireNonNull(reports, "reports");
        Objects.requireNonNull(asOfDate, "asOfDate");
        Objects.requireNonNull(basis, "basis");

        if (reports.isEmpty()) {
            return new SummaryResult(
                    RULE_VERSION,
                    null,
                    null,
                    DataStatus.UNAVAILABLE,
                    List.of("FUNDAMENTALS_UNAVAILABLE"),
                    List.of(),
                    Set.of()
            );
        }

        // Sort reports descending by periodEnd. Tie-break (2026-08-31, Q-47): an annual report and
        // its Q4 share the same period end; the quarter wins so that "newest" is the same report
        // whatever order the repository returned (Constitution I determinism), and stays on the
        // quarterly basis that the TTM path and the period-scoped ratio rules already use. A last
        // tie-break on report id keeps the order total.
        List<ReportPeriod> sorted = reports.stream()
                .sorted(NEWEST_FIRST)
                .toList();

        ReportPeriod newest = sorted.get(0);
        LocalDate basisPeriodEnd = newest.periodEnd();
        String basisPeriodLabel = newest.periodType().equals("QUARTER")
                ? newest.fiscalYear() + "-Q" + newest.fiscalQuarter()
                : String.valueOf(newest.fiscalYear());

        // Evaluate R-010 freshness
        long daysDiff = ChronoUnit.DAYS.between(basisPeriodEnd, asOfDate);
        DataStatus dataStatus;
        List<String> reasonCodes = new ArrayList<>();
        if (daysDiff <= 190) {
            dataStatus = DataStatus.CURRENT;
        } else if (daysDiff <= 280) {
            dataStatus = DataStatus.DELAYED;
            reasonCodes.add("FUNDAMENTALS_DELAYED");
        } else {
            dataStatus = DataStatus.STALE;
            reasonCodes.add("FUNDAMENTALS_STALE");
        }

        Set<UUID> contributingIds = new HashSet<>();
        List<SummaryMetric> summaryMetrics = new ArrayList<>();

        // Group quarter reports for TTM (or fallback to annual report if only annual available)
        List<ReportPeriod> quarterReports = sorted.stream()
                .filter(r -> "QUARTER".equals(r.periodType()))
                .toList();

        List<ReportPeriod> annualReports = sorted.stream()
                .filter(r -> "ANNUAL".equals(r.periodType()))
                .toList();
        List<ReportPeriod> currentTtmPeriods;
        boolean ttmOnAnnualBasis = false;
        // v3 (contract fundamental-summary-v3, Q-60): four quarterly reports are not automatically a
        // twelve-month window. They must be four CONSECUTIVE fiscal quarters and must not be older
        // than an annual report we already hold, or the "TTM" is a sum of scattered periods -- for
        // 60 instruments it was years stale while a current annual report sat unused.
        boolean quarterWindowRejected = false;
        if (basis == AggregateBasis.FISCAL_YEAR) {
            // valuation-v3: the latest visible annual report is the aggregate basis whatever the
            // quarter count; with no annual report there is no fiscal-year aggregate at all.
            currentTtmPeriods = annualReports.isEmpty() ? List.of() : List.of(annualReports.get(0));
            ttmOnAnnualBasis = !annualReports.isEmpty();
        } else if (quarterReports.size() >= 4 && quarterWindowEligible(quarterReports.subList(0, 4), annualReports)) {
            currentTtmPeriods = quarterReports.subList(0, 4);
        } else if (!annualReports.isEmpty()) {
            // v2: fewer than four quarters visible -> latest annual report is the TTM basis.
            // v3: also when the four newest quarters are ineligible.
            quarterWindowRejected = quarterReports.size() >= 4;
            currentTtmPeriods = List.of(annualReports.get(0));
            ttmOnAnnualBasis = true;
        } else if (quarterReports.size() >= 4) {
            // v3 FR-004: ineligible window and no annual report to fall back to. A sum of quarters
            // from different years is not a twelve-month figure; withhold rather than mislabel.
            quarterWindowRejected = true;
            currentTtmPeriods = List.of();
        } else {
            currentTtmPeriods = quarterReports;
        }
        if (quarterWindowRejected) {
            reasonCodes.add(QUARTER_WINDOW_INELIGIBLE);
        }

        for (ReportPeriod q : currentTtmPeriods) {
            if (q.reportId() != null) {
                contributingIds.add(q.reportId());
            }
        }
        if (newest.reportId() != null) {
            contributingIds.add(newest.reportId());
        }

        // TTM Metrics (4 quarters required or 1 annual report)
        int before = summaryMetrics.size();
        // v3: when the window was rejected outright (no annual to fall back to) the aggregates are
        // withheld under that cause, not under the generic "not enough history".
        String unavailableReason = quarterWindowRejected && currentTtmPeriods.isEmpty()
                ? QUARTER_WINDOW_INELIGIBLE : "INSUFFICIENT_HISTORY";
        addTtmSumMetric(summaryMetrics, "NET_PROFIT", "NET_PROFIT_TTM", currentTtmPeriods, unavailableReason);
        addEpsTtmMetric(summaryMetrics, currentTtmPeriods, newest, basis == AggregateBasis.PREFER_QUARTERS,
                unavailableReason);
        addTtmSumMetric(summaryMetrics, "REVENUE", "REVENUE_TTM", currentTtmPeriods, unavailableReason);
        addTtmSumMetric(summaryMetrics, "EBITDA", "EBITDA_TTM", currentTtmPeriods, unavailableReason);
        addTtmSumMetric(summaryMetrics, "DIVIDEND_PER_SHARE", "DIVIDEND_PER_SHARE_TTM", currentTtmPeriods,
                unavailableReason);
        if (ttmOnAnnualBasis) {
            labelAnnualBasis(summaryMetrics, before);
        }

        // Growth: quarterly TTM vs prior TTM when eight quarters are visible; else annual YoY (v2).
        addGrowthMetric(summaryMetrics, contributingIds, "EPS", "EPS_GROWTH_PERCENT",
                "NEGATIVE_OR_ZERO_PRIOR_EPS", quarterReports, currentTtmPeriods, annualReports, basis);
        addGrowthMetric(summaryMetrics, contributingIds, "REVENUE", "REVENUE_GROWTH_PERCENT",
                "NEGATIVE_OR_ZERO_PRIOR_REVENUE", quarterReports, currentTtmPeriods, annualReports, basis);

        // Newest Report Snapshot Metrics
        addLatestMetric(summaryMetrics, "ROE", newest);
        addLatestMetric(summaryMetrics, "ROA", newest);
        addLatestMetric(summaryMetrics, "DEBT_TO_EQUITY", newest);
        addLatestMetric(summaryMetrics, "OPERATING_MARGIN", newest);
        // Cash-flow facts are annual-only from the accepted provider (Feature 008 R-002):
        // read the newest period first, else the latest accepted annual report.
        addLatestMetricWithAnnualFallback(summaryMetrics, "FREE_CASH_FLOW", newest, sorted);
        addLatestMetric(summaryMetrics, "EQUITY_ATTRIBUTABLE_TO_PARENT", newest);
        addLatestMetric(summaryMetrics, "TOTAL_DEBT", newest);
        addLatestMetric(summaryMetrics, "CASH_AND_EQUIVALENTS", newest);
        addLatestMetric(summaryMetrics, "GROSS_PROFIT", newest);
        addLatestMetric(summaryMetrics, "OPERATING_PROFIT", newest);
        addLatestMetric(summaryMetrics, "BVPS", newest);
        addLatestMetric(summaryMetrics, "TRAILING_EPS", newest);
        addAnnualScopedMetric(summaryMetrics, "DIVIDEND_YIELD", newest, sorted);
        addLatestMetric(summaryMetrics, "EV_EBITDA", newest);
        // Feature 009: provider-reported ratios, newest accepted period, stored as observed.
        addLatestMetric(summaryMetrics, "GROSS_MARGIN", newest);
        addLatestMetric(summaryMetrics, "NET_MARGIN", newest);
        addLatestMetric(summaryMetrics, "ROE_TTM", newest);
        addLatestMetric(summaryMetrics, "ROA_TTM", newest);
        addAnnualScopedMetric(summaryMetrics, "ROCE", newest, sorted);
        addLatestMetric(summaryMetrics, "CURRENT_RATIO", newest);
        addLatestMetric(summaryMetrics, "QUICK_RATIO", newest);
        addLatestMetric(summaryMetrics, "CASH_RATIO", newest);
        addLatestMetric(summaryMetrics, "INTEREST_COVERAGE", newest);
        addAnnualScopedMetric(summaryMetrics, "TOTAL_ASSET_TURNOVER", newest, sorted);
        addAnnualScopedMetric(summaryMetrics, "INVENTORY_TURNOVER", newest, sorted);
        addAnnualScopedMetric(summaryMetrics, "RECEIVABLES_TURNOVER", newest, sorted);
        addLatestMetric(summaryMetrics, "DEBT_TO_ASSETS", newest);
        addLatestMetric(summaryMetrics, "LIABILITIES_TO_EQUITY", newest);
        addLatestMetric(summaryMetrics, "EQUITY_TO_ASSETS", newest);
        addLatestMetric(summaryMetrics, "BETA", newest);
        addAnnualScopedMetric(summaryMetrics, "PS", newest, sorted);
        addAnnualScopedMetric(summaryMetrics, "TOTAL_ASSETS_GROWTH_PERCENT", newest, sorted);
        addAnnualScopedMetric(summaryMetrics, "EQUITY_GROWTH_PERCENT", newest, sorted);
        addAnnualScopedMetric(summaryMetrics, "NIM", newest, sorted);
        addLatestMetric(summaryMetrics, "COST_INCOME_RATIO", newest);
        addLatestMetric(summaryMetrics, "LOAN_TO_DEPOSIT", newest);

        return new SummaryResult(
                RULE_VERSION,
                basisPeriodLabel,
                basisPeriodEnd,
                dataStatus,
                reasonCodes,
                summaryMetrics,
                contributingIds
        );
    }

    private void addEpsTtmMetric(
            List<SummaryMetric> target,
            List<ReportPeriod> ttmPeriods,
            ReportPeriod newest,
            boolean allowProviderTrailingFallback,
            String unavailableReason) {
        if (!ttmPeriods.isEmpty() && (ttmPeriods.size() >= 4 || "ANNUAL".equals(ttmPeriods.get(0).periodType()))) {
            BigDecimal sum = getTtmSum("EPS", ttmPeriods);
            if (sum != null) {
                target.add(new SummaryMetric("EPS_TTM", sum, MetricApplicability.DEFINED, null));
                return;
            }
        }
        // Fallback to TRAILING_EPS from ratio snapshot (e.g. for banks / securities). Not on the
        // fiscal-year basis (valuation-v3): the provider's trailing figure is a TTM number and would
        // re-mix the bases the fiscal-year series exists to keep apart.
        if (allowProviderTrailingFallback && newest != null && newest.metrics() != null) {
            for (ReportMetric m : newest.metrics()) {
                if ("TRAILING_EPS".equals(m.metricCode()) && m.applicability() == MetricApplicability.DEFINED && m.value() != null) {
                    // Disclosed (Constitution II): this figure is the provider's TTM EPS, not a sum of
                    // Finvera-imported quarters (independent recomputation 2026-08-31 flagged the silent path).
                    target.add(new SummaryMetric("EPS_TTM", m.value(), MetricApplicability.DEFINED, PROVIDER_TRAILING_EPS));
                    return;
                }
            }
        }
        if (ttmPeriods.size() < 4 && (ttmPeriods.isEmpty() || !"ANNUAL".equals(ttmPeriods.get(0).periodType()))) {
            target.add(new SummaryMetric("EPS_TTM", null, MetricApplicability.MISSING, unavailableReason));
        } else {
            target.add(new SummaryMetric("EPS_TTM", null, MetricApplicability.MISSING, "NO_DATA"));
        }
    }

    private void addTtmSumMetric(
            List<SummaryMetric> target,
            String sourceCode,
            String targetCode,
            List<ReportPeriod> ttmPeriods,
            String unavailableReason) {
        if (ttmPeriods.isEmpty() || (ttmPeriods.size() < 4 && !"ANNUAL".equals(ttmPeriods.get(0).periodType()))) {
            target.add(new SummaryMetric(targetCode, null, MetricApplicability.MISSING, unavailableReason));
            return;
        }
        BigDecimal sum = getTtmSum(sourceCode, ttmPeriods);
        if (sum != null) {
            target.add(new SummaryMetric(targetCode, sum, MetricApplicability.DEFINED, null));
        } else {
            target.add(new SummaryMetric(targetCode, null, MetricApplicability.MISSING, "NO_DATA"));
        }
    }

    /**
     * Period-over-period TTM growth for one source metric, shared by EPS_GROWTH_PERCENT
     * and REVENUE_GROWTH_PERCENT (Feature 003 research R-005) so the two never drift
     * apart into two independently-maintained formulas.
     */
    private void addGrowthMetric(
            List<SummaryMetric> summaryMetrics,
            Set<UUID> contributingIds,
            String sourceMetricCode,
            String targetMetricCode,
            String notApplicableReason,
            List<ReportPeriod> quarterReports,
            List<ReportPeriod> currentTtmPeriods,
            List<ReportPeriod> annualReports,
            AggregateBasis basis) {
        BigDecimal currentTtm = getTtmSum(sourceMetricCode, currentTtmPeriods);
        if (basis == AggregateBasis.FISCAL_YEAR && annualReports.size() < 2) {
            // valuation-v3: growth on the fiscal-year basis needs two annual reports; quarters never
            // substitute, so fewer than two is simply insufficient history.
            summaryMetrics.add(new SummaryMetric(targetMetricCode, null, MetricApplicability.MISSING, "INSUFFICIENT_HISTORY"));
            return;
        }
        boolean eightQuarterWindowUsable = basis != AggregateBasis.FISCAL_YEAR && quarterReports.size() >= 8
                && quarterWindowEligible(quarterReports.subList(0, 8), annualReports);
        if (!eightQuarterWindowUsable && annualReports.size() >= 2) {
            // v2 annual-over-prior-annual fallback (research R-005).
            List<ReportPeriod> latest = List.of(annualReports.get(0));
            List<ReportPeriod> prior = List.of(annualReports.get(1));
            for (ReportPeriod a : List.of(annualReports.get(0), annualReports.get(1))) {
                if (a.reportId() != null) {
                    contributingIds.add(a.reportId());
                }
            }
            BigDecimal latestValue = getTtmSum(sourceMetricCode, latest);
            BigDecimal priorValue = getTtmSum(sourceMetricCode, prior);
            if (latestValue == null || priorValue == null) {
                summaryMetrics.add(new SummaryMetric(targetMetricCode, null, MetricApplicability.MISSING, "INSUFFICIENT_HISTORY"));
            } else if (priorValue.compareTo(BigDecimal.ZERO) <= 0) {
                summaryMetrics.add(new SummaryMetric(targetMetricCode, null, MetricApplicability.NOT_APPLICABLE, notApplicableReason));
            } else {
                BigDecimal growthPercent = DecimalMath.divide12(latestValue, priorValue).subtract(BigDecimal.ONE).multiply(ONE_HUNDRED);
                summaryMetrics.add(new SummaryMetric(targetMetricCode, growthPercent, MetricApplicability.DEFINED, ANNUAL_BASIS));
            }
            return;
        }
        if (quarterReports.size() >= 8 && quarterWindowEligible(quarterReports.subList(0, 8), annualReports)) {
            List<ReportPeriod> priorTtmQuarters = quarterReports.subList(4, 8);
            for (ReportPeriod q : priorTtmQuarters) {
                if (q.reportId() != null) {
                    contributingIds.add(q.reportId());
                }
            }
            BigDecimal priorTtm = getTtmSum(sourceMetricCode, priorTtmQuarters);
            if (currentTtm != null && priorTtm != null) {
                if (priorTtm.compareTo(BigDecimal.ZERO) <= 0) {
                    summaryMetrics.add(new SummaryMetric(
                            targetMetricCode, null, MetricApplicability.NOT_APPLICABLE, notApplicableReason));
                } else {
                    BigDecimal growthRatio = DecimalMath.divide12(currentTtm, priorTtm);
                    BigDecimal growthPercent = growthRatio.subtract(BigDecimal.ONE).multiply(ONE_HUNDRED);
                    summaryMetrics.add(new SummaryMetric(
                            targetMetricCode, growthPercent, MetricApplicability.DEFINED, null));
                }
            } else {
                summaryMetrics.add(new SummaryMetric(
                        targetMetricCode, null, MetricApplicability.MISSING, "INSUFFICIENT_HISTORY"));
            }
        } else {
            summaryMetrics.add(new SummaryMetric(
                    targetMetricCode, null, MetricApplicability.MISSING, "INSUFFICIENT_HISTORY"));
        }
    }

    private static void labelAnnualBasis(List<SummaryMetric> metrics, int fromIndex) {
        for (int i = fromIndex; i < metrics.size(); i++) {
            SummaryMetric m = metrics.get(i);
            // A metric that already discloses its own source (PROVIDER_TRAILING_EPS) keeps that
            // label: it is the provider's trailing figure, not an annual-basis sum.
            if (m.applicability() == MetricApplicability.DEFINED && m.qualityReason() == null) {
                metrics.set(i, new SummaryMetric(m.metricCode(), m.value(), m.applicability(), ANNUAL_BASIS));
            }
        }
    }

    private BigDecimal getTtmSum(String metricCode, List<ReportPeriod> periods) {
        if (periods.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int definedCount = 0;
        for (ReportPeriod p : periods) {
            for (ReportMetric m : p.metrics()) {
                if (m.metricCode().equals(metricCode)) {
                    if (m.applicability() == MetricApplicability.DEFINED && m.value() != null) {
                        sum = sum.add(m.value());
                        definedCount++;
                    }
                    break;
                }
            }
        }
        return definedCount == periods.size() ? sum : null;
    }

    private void addLatestMetricWithAnnualFallback(List<SummaryMetric> target, String metricCode,
            ReportPeriod newest, List<ReportPeriod> sortedDesc) {
        ReportMetric fromNewest = find(newest, metricCode);
        if (fromNewest != null && fromNewest.applicability() == MetricApplicability.DEFINED) {
            target.add(new SummaryMetric(metricCode, fromNewest.value(), fromNewest.applicability(), fromNewest.qualityReason()));
            return;
        }
        for (ReportPeriod period : sortedDesc) {
            if (!"ANNUAL".equals(period.periodType())) {
                continue;
            }
            ReportMetric annual = find(period, metricCode);
            if (annual != null && annual.applicability() == MetricApplicability.DEFINED) {
                target.add(new SummaryMetric(metricCode, annual.value(), annual.applicability(), annual.qualityReason()));
                return;
            }
        }
        addLatestMetric(target, metricCode, newest);
    }

    /** Contract provider-ratio-facts-v2 U-4: newest report first, else latest annual with ANNUAL_BASIS. */
    private void addAnnualScopedMetric(List<SummaryMetric> target, String metricCode,
            ReportPeriod newest, List<ReportPeriod> sortedDesc) {
        ReportMetric fromNewest = find(newest, metricCode);
        if (fromNewest != null && fromNewest.applicability() == MetricApplicability.DEFINED) {
            target.add(new SummaryMetric(metricCode, fromNewest.value(), fromNewest.applicability(), fromNewest.qualityReason()));
            return;
        }
        for (ReportPeriod period : sortedDesc) {
            if (!"ANNUAL".equals(period.periodType())) {
                continue;
            }
            ReportMetric annual = find(period, metricCode);
            if (annual != null && annual.applicability() == MetricApplicability.DEFINED) {
                target.add(new SummaryMetric(metricCode, annual.value(), MetricApplicability.DEFINED, ANNUAL_BASIS));
                return;
            }
        }
        addLatestMetric(target, metricCode, newest);
    }

    private static ReportMetric find(ReportPeriod period, String metricCode) {
        if (period == null || period.metrics() == null) {
            return null;
        }
        for (ReportMetric m : period.metrics()) {
            if (metricCode.equals(m.metricCode())) {
                return m;
            }
        }
        return null;
    }

    private void addLatestMetric(List<SummaryMetric> target, String metricCode, ReportPeriod newest) {
        for (ReportMetric m : newest.metrics()) {
            if (m.metricCode().equals(metricCode)) {
                target.add(new SummaryMetric(
                        metricCode,
                        m.value(),
                        m.applicability(),
                        m.qualityReason()
                ));
                return;
            }
        }
        target.add(new SummaryMetric(
                metricCode,
                null,
                MetricApplicability.MISSING,
                "NOT_REPORTED"
        ));
    }

    public record ReportPeriod(
            UUID reportId,
            String periodType,
            int fiscalYear,
            Integer fiscalQuarter,
            LocalDate periodStart,
            LocalDate periodEnd,
            String reportKind,
            List<ReportMetric> metrics
    ) {}

    public record ReportMetric(
            String metricCode,
            BigDecimal value,
            MetricApplicability applicability,
            String qualityReason
    ) {}

    public record SummaryMetric(
            String metricCode,
            BigDecimal value,
            MetricApplicability applicability,
            String qualityReason
    ) {}

    public record SummaryResult(
            String ruleVersion,
            String basisPeriodLabel,
            LocalDate basisPeriodEnd,
            DataStatus dataStatus,
            List<String> reasonCodes,
            List<SummaryMetric> metrics,
            Set<UUID> contributingReportIds
    ) {}
}
