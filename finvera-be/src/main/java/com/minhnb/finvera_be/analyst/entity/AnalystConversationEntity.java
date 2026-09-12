package com.minhnb.finvera_be.analyst.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analyst_conversation")
public class AnalystConversationEntity {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(nullable = false, length = 120) private String title;
    @Column(name = "title_source", nullable = false, length = 16) private String titleSource;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "last_activity_at", nullable = false) private Instant lastActivityAt;
    @Column(name = "next_sequence_no", nullable = false) private long nextSequenceNo;

    protected AnalystConversationEntity() { }

    public AnalystConversationEntity(UUID id, UUID ownerId, String title, Instant now) {
        this.id = id; this.ownerId = ownerId; this.title = title; this.titleSource = "AUTO";
        this.createdAt = now; this.updatedAt = now; this.lastActivityAt = now; this.nextSequenceNo = 1;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public String getTitleSource() { return titleSource; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public long getNextSequenceNo() { return nextSequenceNo; }

    public long allocateSequence(Instant now) {
        long allocated = nextSequenceNo++;
        updatedAt = now;
        lastActivityAt = now;
        return allocated;
    }

    public void rename(String title, Instant now) {
        this.title = title; this.titleSource = "OWNER"; this.updatedAt = now;
    }

    public void touch(Instant now) { this.updatedAt = now; this.lastActivityAt = now; }
}
