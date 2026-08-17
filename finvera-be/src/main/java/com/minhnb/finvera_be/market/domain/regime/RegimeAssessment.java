package com.minhnb.finvera_be.market.domain.regime;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.FactorDirection;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Immutable result of a single {@code market-regime-v1} calculation. */
public record RegimeAssessment(
        DataStatus dataStatus,
        RegimeLabel label,
        Integer score,
        Integer confidence,
        BigDecimal completeness,
        BigDecimal factorAgreement,
        BigDecimal boundaryDistance,
        boolean renormalized,
        List<String> reasonCodes,
        List<SupportingFactor> factors) {

    public RegimeAssessment {
        Objects.requireNonNull(dataStatus, "dataStatus");
        Objects.requireNonNull(completeness, "completeness");
        Objects.requireNonNull(reasonCodes, "reasonCodes");
        Objects.requireNonNull(factors, "factors");
        reasonCodes = List.copyOf(reasonCodes);
        factors = List.copyOf(factors);
        boolean published = label != null || score != null || confidence != null;
        if (published && (label == null || score == null || confidence == null)) {
            throw new IllegalArgumentException("published assessment requires label, score, and confidence");
        }
        if (score != null && (score < 0 || score > 100 || confidence < 0 || confidence > 100)) {
            throw new IllegalArgumentException("score and confidence must be within 0..100");
        }
    }

    /** Explainable contribution of one approved component to a published regime assessment. */
    public record SupportingFactor(
            MarketRegimeV1.Component component,
            FactorDirection direction,
            BigDecimal normalizedScore,
            BigDecimal originalWeight,
            BigDecimal effectiveWeight,
            BigDecimal contribution) {

        public SupportingFactor {
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(normalizedScore, "normalizedScore");
            Objects.requireNonNull(originalWeight, "originalWeight");
            Objects.requireNonNull(effectiveWeight, "effectiveWeight");
            Objects.requireNonNull(contribution, "contribution");
        }
    }
}
