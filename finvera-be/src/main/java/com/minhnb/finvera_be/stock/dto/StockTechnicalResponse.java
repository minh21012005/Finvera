package com.minhnb.finvera_be.stock.dto;

import com.minhnb.finvera_be.stock.domain.model.DecimalMath;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.AdjustmentStatus;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.ComponentResult;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.IndicatorResult;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService.StockTechnical;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Version 1.0 transport DTO for `GET /stocks/{symbol}/technical` (contracts/stock-detail.openapi.yaml). */
public record StockTechnicalResponse(
        SectionMeta meta,
        String ruleVersion,
        AdjustmentStatus adjustmentStatus,
        String disclaimerCode,
        List<IndicatorResponse> indicators) {

    public static StockTechnicalResponse from(String symbol, StockTechnical technical) {
        return new StockTechnicalResponse(
                SectionMeta.of(symbol, technical.asOf(), technical.tradingDate(), technical.dataStatus(),
                        technical.coherenceKey(), List.of("FINVERA_ACCEPTED"), technical.reasonCodes()),
                technical.ruleVersion(), technical.adjustmentStatus(), "QUANTITATIVE_DECISION_SUPPORT",
                technical.indicators().stream().map(IndicatorResponse::from).toList());
    }

    public record IndicatorResponse(
            String indicatorCode,
            String applicability,
            int requiredBars,
            int availableBars,
            LocalDate windowStartDate,
            LocalDate windowEndDate,
            String reasonCode,
            List<ComponentResponse> components) {
        static IndicatorResponse from(IndicatorResult result) {
            return new IndicatorResponse(result.indicatorCode().name(), result.applicability().name(),
                    result.requiredBars(), result.availableBars(), result.windowStartDate(), result.windowEndDate(),
                    result.reasonCode(), result.components().stream().map(ComponentResponse::from).toList());
        }
    }

    public record ComponentResponse(
            String componentCode, String value, String unit, int displayPrecision, String applicability,
            String reasonCode) {
        static ComponentResponse from(ComponentResult component) {
            return new ComponentResponse(component.componentCode().name(),
                    displayDecimal(component.value(), component.displayPrecision()), component.unit().name(),
                    component.displayPrecision(), component.applicability().name(), component.reasonCode());
        }
    }

    // Rule U-4: the stored/computed value stays unrounded at scale 12 (needed for
    // SC-003 replay determinism); the transport boundary is where display
    // rounding happens exactly once, via the already-established DecimalMath
    // helper built for this in Phase 2 but not yet used until this section.
    private static String displayDecimal(BigDecimal value, int displayPrecision) {
        return value == null ? null : DecimalMath.roundForDisplay(value, displayPrecision).toPlainString();
    }
}
