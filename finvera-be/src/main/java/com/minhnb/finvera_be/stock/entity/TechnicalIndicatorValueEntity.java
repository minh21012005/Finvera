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
@Table(name = "technical_indicator_value")
@IdClass(TechnicalIndicatorValueEntity.Key.class)
public class TechnicalIndicatorValueEntity {
    @Id @Column(name = "result_id") private UUID resultId;
    @Id @Column(name = "component_code") private String componentCode;
    @Column(precision = 28, scale = 12) private BigDecimal value;
    private String unit;
    private String applicability;
    @Column(name = "quality_reason") private String qualityReason;

    protected TechnicalIndicatorValueEntity() { }

    public TechnicalIndicatorValueEntity(UUID resultId, String componentCode, BigDecimal value, String unit,
            String applicability, String qualityReason) {
        this.resultId = resultId; this.componentCode = componentCode; this.value = value; this.unit = unit;
        this.applicability = applicability; this.qualityReason = qualityReason;
    }

    public UUID getResultId() { return resultId; }
    public String getComponentCode() { return componentCode; }
    public BigDecimal getValue() { return value; }
    public String getUnit() { return unit; }
    public String getApplicability() { return applicability; }
    public String getQualityReason() { return qualityReason; }

    public static final class Key implements Serializable {
        private UUID resultId;
        private String componentCode;
        public Key() { }
        public Key(UUID resultId, String componentCode) { this.resultId = resultId; this.componentCode = componentCode; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(resultId, key.resultId)
                    && Objects.equals(componentCode, key.componentCode);
        }
        @Override public int hashCode() { return Objects.hash(resultId, componentCode); }
    }
}
