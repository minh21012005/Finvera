package com.minhnb.finvera_be.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.sql.Types;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "valuation_assessment_input")
@IdClass(ValuationAssessmentInputEntity.Key.class)
public class ValuationAssessmentInputEntity {
    @Id @Column(name = "assessment_id") private UUID assessmentId;
    @Id @Column(name = "input_role") private String inputRole;
    @Column(name = "price_observation_id") private UUID priceObservationId;
    @Column(name = "daily_bar_id") private UUID dailyBarId;
    @Column(name = "fundamental_summary_id") private UUID fundamentalSummaryId;
    @Column(name = "equity_profile_id") private UUID equityProfileId;
    @Column(name = "input_set_hash", columnDefinition = "char(64)")
    @JdbcTypeCode(Types.CHAR)
    private String inputSetHash;

    protected ValuationAssessmentInputEntity() { }

    public ValuationAssessmentInputEntity(UUID assessmentId, String inputRole, UUID priceObservationId,
            UUID dailyBarId, UUID fundamentalSummaryId, UUID equityProfileId, String inputSetHash) {
        this.assessmentId = assessmentId; this.inputRole = inputRole; this.priceObservationId = priceObservationId;
        this.dailyBarId = dailyBarId; this.fundamentalSummaryId = fundamentalSummaryId;
        this.equityProfileId = equityProfileId; this.inputSetHash = inputSetHash;
    }

    public UUID getAssessmentId() { return assessmentId; }
    public String getInputRole() { return inputRole; }
    public UUID getPriceObservationId() { return priceObservationId; }
    public UUID getDailyBarId() { return dailyBarId; }
    public UUID getFundamentalSummaryId() { return fundamentalSummaryId; }
    public UUID getEquityProfileId() { return equityProfileId; }
    public String getInputSetHash() { return inputSetHash; }

    public static final class Key implements Serializable {
        private UUID assessmentId;
        private String inputRole;
        public Key() { }
        public Key(UUID assessmentId, String inputRole) { this.assessmentId = assessmentId; this.inputRole = inputRole; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(assessmentId, key.assessmentId)
                    && Objects.equals(inputRole, key.inputRole);
        }
        @Override public int hashCode() { return Objects.hash(assessmentId, inputRole); }
    }
}
