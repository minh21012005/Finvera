package com.minhnb.finvera_be.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "strategy_signal_input")
@IdClass(StrategySignalInputEntity.Key.class)
public class StrategySignalInputEntity {
    @Id @Column(name = "signal_id") private UUID signalId;
    @Id @Column(name = "input_role") private String inputRole;
    @Column(name = "technical_indicator_result_id") private UUID technicalIndicatorResultId;
    @Column(name = "daily_bar_id") private UUID dailyBarId;
    @Column(name = "regime_assessment_id") private UUID regimeAssessmentId;

    protected StrategySignalInputEntity() { }

    public StrategySignalInputEntity(UUID signalId, String inputRole, UUID technicalIndicatorResultId,
            UUID dailyBarId, UUID regimeAssessmentId) {
        this.signalId = signalId; this.inputRole = inputRole;
        this.technicalIndicatorResultId = technicalIndicatorResultId; this.dailyBarId = dailyBarId;
        this.regimeAssessmentId = regimeAssessmentId;
    }

    public UUID getSignalId() { return signalId; }
    public String getInputRole() { return inputRole; }
    public UUID getTechnicalIndicatorResultId() { return technicalIndicatorResultId; }
    public UUID getDailyBarId() { return dailyBarId; }
    public UUID getRegimeAssessmentId() { return regimeAssessmentId; }

    public static final class Key implements Serializable {
        private UUID signalId;
        private String inputRole;
        public Key() { }
        public Key(UUID signalId, String inputRole) { this.signalId = signalId; this.inputRole = inputRole; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(signalId, key.signalId)
                    && Objects.equals(inputRole, key.inputRole);
        }
        @Override public int hashCode() { return Objects.hash(signalId, inputRole); }
    }
}
