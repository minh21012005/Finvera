package com.minhnb.finvera_be.stock.domain.fundamentals;

import com.minhnb.finvera_be.stock.domain.model.DecimalMath;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * FR-007, DATA-006, DATA-007.
 * Domain validation and normalization for incoming fundamental reports.
 * Normalizes unit scale to base VND and drops unmapped metric codes.
 */
public final class FundamentalReportAcceptance {

    public static final String CATALOG_VERSION_V1 = "fundamental-metric-catalog-v1";

    public static final Set<String> ALLOWED_METRIC_CODES = Set.of(
            "REVENUE",
            "GROSS_PROFIT",
            "OPERATING_PROFIT",
            "NET_PROFIT",
            "EPS",
            "ROE",
            "ROA",
            "DEBT_TO_EQUITY",
            "OPERATING_MARGIN",
            "FREE_CASH_FLOW",
            "DIVIDEND_PER_SHARE",
            "EQUITY_ATTRIBUTABLE_TO_PARENT",
            "TOTAL_DEBT",
            "CASH_AND_EQUIVALENTS",
            "EBITDA"
    );

    public AcceptanceResult accept(ReportInput input) {
        Objects.requireNonNull(input, "input");

        // Period validation
        boolean isQuarter = "QUARTER".equals(input.periodType());
        boolean isAnnual = "ANNUAL".equals(input.periodType());
        if (!isQuarter && !isAnnual) {
            return AcceptanceResult.rejected("INVALID_PERIOD");
        }
        if (isQuarter && (input.fiscalQuarter() == null || input.fiscalQuarter() < 1 || input.fiscalQuarter() > 4)) {
            return AcceptanceResult.rejected("INVALID_PERIOD");
        }
        if (isAnnual && input.fiscalQuarter() != null) {
            return AcceptanceResult.rejected("INVALID_PERIOD");
        }
        if (input.periodEnd().isBefore(input.periodStart())) {
            return AcceptanceResult.rejected("INVALID_PERIOD");
        }

        int unitScale = input.unitScale() <= 0 ? 1 : input.unitScale();
        BigDecimal scaleMultiplier = BigDecimal.valueOf(unitScale);

        List<AcceptedMetric> acceptedMetrics = new ArrayList<>();
        int droppedCount = 0;

        for (MetricInput m : input.metrics()) {
            if (!ALLOWED_METRIC_CODES.contains(m.metricCode())) {
                droppedCount++;
                continue;
            }

            MetricApplicability applicability;
            try {
                applicability = MetricApplicability.valueOf(m.applicability());
            } catch (Exception e) {
                return AcceptanceResult.rejected("INVALID_METRIC");
            }

            if (applicability == MetricApplicability.DEFINED) {
                if (m.value() == null) {
                    return AcceptanceResult.rejected("INVALID_METRIC");
                }
                // Normalize value with unitScale
                BigDecimal normalizedValue = m.value().multiply(scaleMultiplier);
                acceptedMetrics.add(new AcceptedMetric(
                        m.metricCode(),
                        normalizedValue,
                        applicability,
                        m.qualityReason()
                ));
            } else if (applicability == MetricApplicability.NOT_APPLICABLE) {
                if (m.value() != null) {
                    return AcceptanceResult.rejected("INVALID_METRIC");
                }
                acceptedMetrics.add(new AcceptedMetric(
                        m.metricCode(),
                        null,
                        applicability,
                        m.qualityReason() != null ? m.qualityReason() : "NOT_APPLICABLE"
                ));
            } else { // MISSING
                acceptedMetrics.add(new AcceptedMetric(
                        m.metricCode(),
                        null,
                        applicability,
                        m.qualityReason() != null ? m.qualityReason() : "MISSING"
                ));
            }
        }

        return AcceptanceResult.accepted(acceptedMetrics, droppedCount);
    }

    public String periodIdentityKey(ReportInput input) {
        return input.reportKind() + "|" + input.periodType() + "|" + input.fiscalYear()
                + "|" + (input.fiscalQuarter() == null ? "" : input.fiscalQuarter());
    }

    public record ReportInput(
            String periodType,
            int fiscalYear,
            Integer fiscalQuarter,
            LocalDate periodStart,
            LocalDate periodEnd,
            String reportKind,
            String auditStatus,
            String currency,
            int unitScale,
            String catalogVersion,
            List<MetricInput> metrics
    ) {}

    public record MetricInput(
            String metricCode,
            BigDecimal value,
            String applicability,
            String qualityReason
    ) {}

    public record AcceptedMetric(
            String metricCode,
            BigDecimal value,
            MetricApplicability applicability,
            String qualityReason
    ) {}

    public record AcceptanceResult(
            boolean accepted,
            String reasonCode,
            List<AcceptedMetric> metrics,
            int droppedMetricCount
    ) {
        public static AcceptanceResult accepted(List<AcceptedMetric> metrics, int droppedMetricCount) {
            return new AcceptanceResult(true, null, metrics, droppedMetricCount);
        }

        public static AcceptanceResult rejected(String reasonCode) {
            return new AcceptanceResult(false, reasonCode, List.of(), 0);
        }
    }
}
