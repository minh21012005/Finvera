package com.minhnb.finvera_be.stock.dto;

import com.minhnb.finvera_be.stock.domain.model.DecimalMath;
import com.minhnb.finvera_be.stock.service.ValuationService.StockValuation;
import com.minhnb.finvera_be.stock.service.ValuationService.ValuationMetric;
import java.math.BigDecimal;
import java.util.List;

/** Version 1.0 transport DTO for `GET /stocks/{symbol}/valuation` (contracts/stock-detail.openapi.yaml). */
public record StockValuationResponse(
        SectionMeta meta,
        String ruleVersion,
        boolean published,
        String classification,
        String score,
        Integer displayedScore,
        Integer confidence,
        String disclaimerCode,
        BasisResponse basis,
        List<MetricResponse> metrics) {

    public static StockValuationResponse from(String symbol, StockValuation valuation) {
        BasisResponse basis = new BasisResponse(
                valuation.usedOwnHistory(),
                valuation.usedSector(),
                valuation.sector(),
                valuation.sectorScheme(),
                valuation.sectorSchemeVersion(),
                valuation.sectorConstituentCount(),
                valuation.historyPointCount()
        );

        List<MetricResponse> metrics = valuation.metrics().stream()
                .map(MetricResponse::from)
                .toList();

        return new StockValuationResponse(
                SectionMeta.of(
                        symbol,
                        valuation.asOf(),
                        valuation.tradingDate(),
                        valuation.dataStatus(),
                        valuation.coherenceKey(),
                        List.of("FINVERA_ACCEPTED"),
                        valuation.reasonCodes()
                ),
                valuation.ruleVersion(),
                valuation.published(),
                valuation.classification() != null ? valuation.classification().name() : null,
                valuation.score() != null ? valuation.score().toPlainString() : null,
                valuation.displayedScore(),
                valuation.confidence(),
                "QUANTITATIVE_DECISION_SUPPORT",
                basis,
                metrics
        );
    }

    public record BasisResponse(
            boolean usedOwnHistory,
            boolean usedSector,
            String sector,
            String sectorScheme,
            String sectorSchemeVersion,
            Integer sectorConstituentCount,
            Integer historyPointCount) {}

    public record MetricResponse(
            String metricCode,
            String value,
            String applicability,
            String ownHistoryPercentile,
            String sectorPercentile,
            String effectiveWeight,
            String reasonCode) {

        static MetricResponse from(ValuationMetric metric) {
            return new MetricResponse(
                    metric.metricCode(),
                    displayDecimal(metric.value(), 2),
                    metric.applicability().name(),
                    displayDecimal(metric.ownHistoryPercentile(), 2),
                    displayDecimal(metric.sectorPercentile(), 2),
                    metric.effectiveWeight() != null ? metric.effectiveWeight().toPlainString() : null,
                    metric.reasonCode()
            );
        }
    }

    private static String displayDecimal(BigDecimal value, int displayPrecision) {
        return value == null ? null : DecimalMath.roundForDisplay(value, displayPrecision).toPlainString();
    }
}
