package com.minhnb.finvera_be.market.entity;

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
@Table(name = "regime_assessment_input")
@IdClass(MarketRegimeAssessmentInputEntity.Key.class)
public class MarketRegimeAssessmentInputEntity {
    @Id @Column(name = "assessment_id") private UUID assessmentId;
    @Id @Column(name = "input_role") private String inputRole;
    @Column(name = "index_snapshot_id") private UUID indexSnapshotId;
    @Column(name = "breadth_snapshot_id") private UUID breadthSnapshotId;
    @Column(name = "price_observation_id") private UUID priceObservationId;
    @Column(name = "input_set_hash", columnDefinition = "char(64)")
    @JdbcTypeCode(Types.CHAR)
    private String inputSetHash;

    protected MarketRegimeAssessmentInputEntity() { }

    public MarketRegimeAssessmentInputEntity(UUID assessmentId, String inputRole, UUID indexSnapshotId,
            UUID breadthSnapshotId, UUID priceObservationId, String inputSetHash) {
        this.assessmentId = assessmentId; this.inputRole = inputRole; this.indexSnapshotId = indexSnapshotId;
        this.breadthSnapshotId = breadthSnapshotId; this.priceObservationId = priceObservationId;
        this.inputSetHash = inputSetHash;
    }

    public UUID getAssessmentId() { return assessmentId; }
    public String getInputRole() { return inputRole; }
    public UUID getIndexSnapshotId() { return indexSnapshotId; }
    public UUID getBreadthSnapshotId() { return breadthSnapshotId; }
    public UUID getPriceObservationId() { return priceObservationId; }
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
