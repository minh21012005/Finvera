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
     */
    public static final String RULE_VERSION = "fundamental-summary-v2";
    public static final String ANNUAL_BASIS = "ANNUAL_BASIS";
    /**
     * Feature 011 (contract provider-ratio-facts-v2 U-4): codes the provider only reports on an
     * annual basis. Read from the newest report when present, else from the latest annual report
     * and labelled {@code ANNUAL_BASIS}.
     */
    public static final Set<String> ANNUAL_ONLY_CODES = Set.of(
            "ROCE", "NIM", "TOTAL_ASSET_TURNOVER", "INVENTORY_TURNOVER", "RECEIVABLES_TURNOVER",
            "DIVIDEND_YIELD", "PS", "TOTAL_ASSETS_GROWTH_PERCENT", "EQUITY_GROWTH_PERCENT");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public SummaryResult calculate(List<ReportPeriod> reports, LocalDate asOfDate) {
        Objects.requireNonNull(reports, "reports");
        Objects.requireNonNull(asOfDate, "asOfDate");

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

        // Sort reports descending by periodEnd
        List<ReportPeriod> sorted = reports.stream()
                .sorted(Comparator.comparing(ReportPeriod::periodEnd).reversed())
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
        if (quarterReports.size() >= 4) {
            currentTtmPeriods = quarterReports.subList(0, 4);
        } else if (!annualReports.isEmpty()) {
            // v2: fewer than four quarters visible -> latest annual report is the TTM basis.
            currentTtmPeriods = List.of(annualReports.get(0));
            ttmOnAnnualBasis = true;
        } else {
            currentTtmPeriods = quarterReports;
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
        addTtmSumMetric(summaryMetrics, "NET_PROFIT", "NET_PROFIT_TTM", currentTtmPeriods);
        addEpsTtmMetric(summaryMetrics, currentTtmPeriods, newest);
        addTtmSumMetric(summaryMetrics, "REVENUE", "REVENUE_TTM", currentTtmPeriods);
        addTtmSumMetric(summaryMetrics, "EBITDA", "EBITDA_TTM", currentTtmPeriods);
        addTtmSumMetric(summaryMetrics, "DIVIDEND_PER_SHARE", "DIVIDEND_PER_SHARE_TTM", currentTtmPeriods);
        if (ttmOnAnnualBasis) {
            labelAnnualBasis(summaryMetrics, before);
        }

        // Growth: quarterly TTM vs prior TTM when eight quarters are visible; else annual YoY (v2).
        addGrowthMetric(summaryMetrics, contributingIds, "EPS", "EPS_GROWTH_PERCENT",
                "NEGATIVE_OR_ZERO_PRIOR_EPS", quarterReports, currentTtmPeriods, annualReports);
        addGrowthMetric(summaryMetrics, contributingIds, "REVENUE", "REVENUE_GROWTH_PERCENT",
                "NEGATIVE_OR_ZERO_PRIOR_REVENUE", quarterReports, currentTtmPeriods, annualReports);

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
            ReportPeriod newest) {
        if (!ttmPeriods.isEmpty() && (ttmPeriods.size() >= 4 || "ANNUAL".equals(ttmPeriods.get(0).periodType()))) {
            BigDecimal sum = getTtmSum("EPS", ttmPeriods);
            if (sum != null) {
                target.add(new SummaryMetric("EPS_TTM", sum, MetricApplicability.DEFINED, null));
                return;
            }
        }
        // Fallback to TRAILING_EPS from ratio snapshot (e.g. for banks / securities)
        if (newest != null && newest.metrics() != null) {
            for (ReportMetric m : newest.metrics()) {
                if ("TRAILING_EPS".equals(m.metricCode()) && m.applicability() == MetricApplicability.DEFINED && m.value() != null) {
                    target.add(new SummaryMetric("EPS_TTM", m.value(), MetricApplicability.DEFINED, null));
                    return;
                }
            }
        }
        if (ttmPeriods.size() < 4 && (ttmPeriods.isEmpty() || !"ANNUAL".equals(ttmPeriods.get(0).periodType()))) {
            target.add(new SummaryMetric("EPS_TTM", null, MetricApplicability.MISSING, "INSUFFICIENT_HISTORY"));
        } else {
            target.add(new SummaryMetric("EPS_TTM", null, MetricApplicability.MISSING, "NO_DATA"));
        }
    }

    private void addTtmSumMetric(
            List<SummaryMetric> target,
            String sourceCode,
            String targetCode,
            List<ReportPeriod> ttmPeriods) {
        if (ttmPeriods.isEmpty() || (ttmPeriods.size() < 4 && !"ANNUAL".equals(ttmPeriods.get(0).periodType()))) {
            target.add(new SummaryMetric(targetCode, null, MetricApplicability.MISSING, "INSUFFICIENT_HISTORY"));
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
            List<ReportPeriod> annualReports) {
        BigDecimal currentTtm = getTtmSum(sourceMetricCode, currentTtmPeriods);
        if (quarterReports.size() < 8 && annualReports.size() >= 2) {
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
        if (quarterReports.size() >= 8) {
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
            if (m.applicability() == MetricApplicability.DEFINED) {
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
