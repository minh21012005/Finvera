package com.minhnb.finvera_be.stock.dto;

import com.minhnb.finvera_be.stock.domain.model.DecimalMath;
import com.minhnb.finvera_be.stock.service.FundamentalReportService.FundamentalMetric;
import com.minhnb.finvera_be.stock.service.FundamentalReportService.StockFundamentals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Version 1.0 transport DTO for `GET /stocks/{symbol}/fundamentals` (contracts/stock-detail.openapi.yaml). */
public record StockFundamentalsResponse(
        SectionMeta meta,
        PeriodResponse period,
        List<MetricResponse> metrics) {

    public static StockFundamentalsResponse from(String symbol, StockFundamentals fundamentals) {
        PeriodResponse period = fundamentals.periodType() != null
                ? new PeriodResponse(
                        fundamentals.basisPeriodLabel(),
                        fundamentals.periodType(),
                        fundamentals.fiscalYear(),
                        fundamentals.fiscalQuarter(),
                        fundamentals.periodStart(),
                        fundamentals.periodEnd(),
                        fundamentals.reportKind(),
                        fundamentals.auditStatus(),
                        fundamentals.currency(),
                        fundamentals.restated())
                : null;

        List<MetricResponse> metrics = fundamentals.metrics().stream()
                .map(MetricResponse::from)
                .toList();

        return new StockFundamentalsResponse(
                SectionMeta.of(
                        symbol,
                        fundamentals.asOf(),
                        fundamentals.tradingDate(),
                        fundamentals.dataStatus(),
                        fundamentals.coherenceKey(),
                        List.of("FINVERA_ACCEPTED"),
                        fundamentals.reasonCodes()
                ),
                period,
                metrics
        );
    }

    public record PeriodResponse(
            String label,
            String periodType,
            int fiscalYear,
            Integer fiscalQuarter,
            LocalDate periodStart,
            LocalDate periodEnd,
            String reportKind,
            String auditStatus,
            String currency,
            boolean restated) {}

    public record MetricResponse(
            String metricCode,
            String value,
            String unit,
            int displayPrecision,
            String applicability,
            String reasonCode) {

        static MetricResponse from(FundamentalMetric metric) {
            return new MetricResponse(
                    metric.metricCode(),
                    displayDecimal(metric.value(), metric.displayPrecision()),
                    metric.unit(),
                    metric.displayPrecision(),
                    metric.applicability().name(),
                    metric.reasonCode()
            );
        }
    }

    private static String displayDecimal(BigDecimal value, int displayPrecision) {
        return value == null ? null : DecimalMath.roundForDisplay(value, displayPrecision).toPlainString();
    }
}
