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

    public static final String RULE_VERSION = "fundamental-summary-v1";
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

        List<ReportPeriod> currentTtmPeriods;
        if (quarterReports.size() >= 4) {
            currentTtmPeriods = quarterReports.subList(0, 4);
        } else if (quarterReports.isEmpty() && "ANNUAL".equals(newest.periodType())) {
            currentTtmPeriods = List.of(newest);
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
        addTtmSumMetric(summaryMetrics, "NET_PROFIT", "NET_PROFIT_TTM", currentTtmPeriods);
        addTtmSumMetric(summaryMetrics, "EPS", "EPS_TTM", currentTtmPeriods);
        addTtmSumMetric(summaryMetrics, "REVENUE", "REVENUE_TTM", currentTtmPeriods);
        addTtmSumMetric(summaryMetrics, "EBITDA", "EBITDA_TTM", currentTtmPeriods);
        addTtmSumMetric(summaryMetrics, "DIVIDEND_PER_SHARE", "DIVIDEND_PER_SHARE_TTM", currentTtmPeriods);

        // EPS Growth Percent
        BigDecimal currentTtmEps = getTtmSum("EPS", currentTtmPeriods);
        if (quarterReports.size() >= 8) {
            List<ReportPeriod> priorTtmQuarters = quarterReports.subList(4, 8);
            for (ReportPeriod q : priorTtmQuarters) {
                if (q.reportId() != null) {
                    contributingIds.add(q.reportId());
                }
            }
            BigDecimal priorTtmEps = getTtmSum("EPS", priorTtmQuarters);
            if (currentTtmEps != null && priorTtmEps != null) {
                if (priorTtmEps.compareTo(BigDecimal.ZERO) <= 0) {
                    summaryMetrics.add(new SummaryMetric(
                            "EPS_GROWTH_PERCENT",
                            null,
                            MetricApplicability.NOT_APPLICABLE,
                            "NEGATIVE_OR_ZERO_PRIOR_EPS"
                    ));
                } else {
                    BigDecimal growthRatio = DecimalMath.divide12(currentTtmEps, priorTtmEps);
                    BigDecimal growthPercent = growthRatio.subtract(BigDecimal.ONE).multiply(ONE_HUNDRED);
                    summaryMetrics.add(new SummaryMetric(
                            "EPS_GROWTH_PERCENT",
                            growthPercent,
                            MetricApplicability.DEFINED,
                            null
                    ));
                }
            } else {
                summaryMetrics.add(new SummaryMetric(
                        "EPS_GROWTH_PERCENT",
                        null,
                        MetricApplicability.MISSING,
                        "INSUFFICIENT_HISTORY"
                ));
            }
        } else {
            summaryMetrics.add(new SummaryMetric(
                    "EPS_GROWTH_PERCENT",
                    null,
                    MetricApplicability.MISSING,
                    "INSUFFICIENT_HISTORY"
            ));
        }

        // Newest Report Snapshot Metrics
        addLatestMetric(summaryMetrics, "ROE", newest);
        addLatestMetric(summaryMetrics, "ROA", newest);
        addLatestMetric(summaryMetrics, "DEBT_TO_EQUITY", newest);
        addLatestMetric(summaryMetrics, "OPERATING_MARGIN", newest);
        addLatestMetric(summaryMetrics, "FREE_CASH_FLOW", newest);
        addLatestMetric(summaryMetrics, "EQUITY_ATTRIBUTABLE_TO_PARENT", newest);
        addLatestMetric(summaryMetrics, "TOTAL_DEBT", newest);
        addLatestMetric(summaryMetrics, "CASH_AND_EQUIVALENTS", newest);
        addLatestMetric(summaryMetrics, "GROSS_PROFIT", newest);
        addLatestMetric(summaryMetrics, "OPERATING_PROFIT", newest);

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
