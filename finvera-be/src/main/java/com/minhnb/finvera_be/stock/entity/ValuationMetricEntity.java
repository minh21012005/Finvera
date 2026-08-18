package com.minhnb.finvera_be.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "valuation_metric")
@IdClass(ValuationMetricEntity.Key.class)
public class ValuationMetricEntity {
    @Id @Column(name = "assessment_id") private UUID assessmentId;
    @Id @Column(name = "metric_code") private String metricCode;
    @Column(precision = 24, scale = 12) private BigDecimal value;
    private String applicability;
    @Column(name = "own_history_percentile", precision = 6, scale = 3) private BigDecimal ownHistoryPercentile;
    @Column(name = "sector_percentile", precision = 6, scale = 3) private BigDecimal sectorPercentile;
    @Column(name = "effective_weight", precision = 13, scale = 12) private BigDecimal effectiveWeight;
    @Column(name = "quality_reason") private String qualityReason;

    protected ValuationMetricEntity() { }

    public ValuationMetricEntity(UUID assessmentId, String metricCode, BigDecimal value, String applicability,
            BigDecimal ownHistoryPercentile, BigDecimal sectorPercentile, BigDecimal effectiveWeight,
            String qualityReason) {
        this.assessmentId = assessmentId; this.metricCode = metricCode; this.value = value;
        this.applicability = applicability; this.ownHistoryPercentile = ownHistoryPercentile;
        this.sectorPercentile = sectorPercentile; this.effectiveWeight = effectiveWeight;
        this.qualityReason = qualityReason;
    }

    public UUID getAssessmentId() { return assessmentId; }
    public String getMetricCode() { return metricCode; }
    public BigDecimal getValue() { return value; }
    public String getApplicability() { return applicability; }
    public BigDecimal getOwnHistoryPercentile() { return ownHistoryPercentile; }
    public BigDecimal getSectorPercentile() { return sectorPercentile; }
    public BigDecimal getEffectiveWeight() { return effectiveWeight; }
    public String getQualityReason() { return qualityReason; }

    public static final class Key implements Serializable {
        private UUID assessmentId;
        private String metricCode;
        public Key() { }
        public Key(UUID assessmentId, String metricCode) { this.assessmentId = assessmentId; this.metricCode = metricCode; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(assessmentId, key.assessmentId)
                    && Objects.equals(metricCode, key.metricCode);
        }
        @Override public int hashCode() { return Objects.hash(assessmentId, metricCode); }
    }
}
