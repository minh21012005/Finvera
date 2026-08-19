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
@Table(name = "strategy_signal_risk_factor")
@IdClass(StrategySignalRiskFactorEntity.Key.class)
public class StrategySignalRiskFactorEntity {
    @Id @Column(name = "signal_id") private UUID signalId;
    @Id @Column(name = "factor_code") private String factorCode;
    @Column(name = "input_value", precision = 28, scale = 12) private BigDecimal inputValue;
    @Column(name = "factor_score") private Short factorScore;
    private String applicability;
    @Column(name = "quality_reason") private String qualityReason;

    protected StrategySignalRiskFactorEntity() { }

    public StrategySignalRiskFactorEntity(UUID signalId, String factorCode, BigDecimal inputValue,
            Integer factorScore, String applicability, String qualityReason) {
        this.signalId = signalId; this.factorCode = factorCode; this.inputValue = inputValue;
        this.factorScore = factorScore == null ? null : factorScore.shortValue();
        this.applicability = applicability; this.qualityReason = qualityReason;
    }

    public UUID getSignalId() { return signalId; }
    public String getFactorCode() { return factorCode; }
    public BigDecimal getInputValue() { return inputValue; }
    public Integer getFactorScore() { return factorScore == null ? null : factorScore.intValue(); }
    public String getApplicability() { return applicability; }
    public String getQualityReason() { return qualityReason; }

    public static final class Key implements Serializable {
        private UUID signalId;
        private String factorCode;
        public Key() { }
        public Key(UUID signalId, String factorCode) { this.signalId = signalId; this.factorCode = factorCode; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(signalId, key.signalId)
                    && Objects.equals(factorCode, key.factorCode);
        }
        @Override public int hashCode() { return Objects.hash(signalId, factorCode); }
    }
}
