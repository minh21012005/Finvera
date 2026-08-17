package com.minhnb.finvera_be.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import java.util.Objects;

@Entity
@Table(name = "breadth_snapshot_input")
@IdClass(MarketBreadthSnapshotInputEntity.Key.class)
public class MarketBreadthSnapshotInputEntity {
    @Id @Column(name = "breadth_snapshot_id") private UUID breadthSnapshotId;
    @Id @Column(name = "instrument_id") private UUID instrumentId;
    @Column(name = "price_observation_id") private UUID priceObservationId;
    private String classification;
    @Column(name = "reason_code") private String reasonCode;
    protected MarketBreadthSnapshotInputEntity() { }
    public MarketBreadthSnapshotInputEntity(UUID breadthSnapshotId, UUID instrumentId, UUID priceObservationId,
            String classification, String reasonCode) {
        this.breadthSnapshotId = breadthSnapshotId; this.instrumentId = instrumentId;
        this.priceObservationId = priceObservationId; this.classification = classification; this.reasonCode = reasonCode;
    }
    public UUID getBreadthSnapshotId() { return breadthSnapshotId; }
    public UUID getInstrumentId() { return instrumentId; }
    public UUID getPriceObservationId() { return priceObservationId; }
    public String getClassification() { return classification; }
    public String getReasonCode() { return reasonCode; }
    public static final class Key implements Serializable {
        private UUID breadthSnapshotId;
        private UUID instrumentId;
        public Key() { }
        public Key(UUID breadthSnapshotId, UUID instrumentId) {
            this.breadthSnapshotId = breadthSnapshotId;
            this.instrumentId = instrumentId;
        }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(breadthSnapshotId, key.breadthSnapshotId)
                    && Objects.equals(instrumentId, key.instrumentId);
        }
        @Override public int hashCode() { return Objects.hash(breadthSnapshotId, instrumentId); }
    }
}
