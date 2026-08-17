package com.minhnb.finvera_be.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "regime_factor")
public class MarketRegimeFactorEntity {
    @Id private UUID id;
    @Column(name = "assessment_id") private UUID assessmentId;
    @Column(name = "factor_code") private String factorCode;
    private String direction;
    @Column(name = "raw_observations", columnDefinition = "jsonb") @JdbcTypeCode(SqlTypes.JSON) private String rawObservations;
    @Column(name = "normalized_score", precision = 8, scale = 4) private BigDecimal normalizedScore;
    @Column(precision = 8, scale = 6) private BigDecimal weight;
    @Column(name = "effective_weight", precision = 8, scale = 6) private BigDecimal effectiveWeight;
    @Column(precision = 10, scale = 6) private BigDecimal contribution;
    @Column(name = "description_code") private String descriptionCode;

    protected MarketRegimeFactorEntity() { }

    public MarketRegimeFactorEntity(UUID id, UUID assessmentId, String factorCode, String direction,
            BigDecimal normalizedScore, BigDecimal weight, BigDecimal effectiveWeight, BigDecimal contribution) {
        this.id = id; this.assessmentId = assessmentId; this.factorCode = factorCode; this.direction = direction;
        this.rawObservations = "[]"; this.normalizedScore = normalizedScore; this.weight = weight;
        this.effectiveWeight = effectiveWeight; this.contribution = contribution;
        this.descriptionCode = "REGIME_FACTOR_" + factorCode + "_V1";
    }

    public UUID getAssessmentId() { return assessmentId; }
    public String getFactorCode() { return factorCode; }
    public String getDirection() { return direction; }
    public BigDecimal getNormalizedScore() { return normalizedScore; }
    public BigDecimal getWeight() { return weight; }
    public BigDecimal getEffectiveWeight() { return effectiveWeight; }
    public BigDecimal getContribution() { return contribution; }
    public String getDescriptionCode() { return descriptionCode; }
}
