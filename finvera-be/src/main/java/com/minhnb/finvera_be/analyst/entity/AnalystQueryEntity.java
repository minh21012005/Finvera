package com.minhnb.finvera_be.analyst.entity;

import com.minhnb.finvera_be.analyst.domain.AnalystQueryOutcome;
import com.minhnb.finvera_be.analyst.domain.AnalystRequestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "analyst_query")
public class AnalystQueryEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 16)
    private AnalystRequestType requestType;

    @Column(name = "question_preview", nullable = false, length = 300)
    private String questionPreview;

    @Column(name = "question_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(Types.CHAR)
    private String questionHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    private AnalystQueryOutcome outcome;

    @Column(name = "tool_call_bound_reached", nullable = false)
    private boolean toolCallBoundReached;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AnalystQueryEntity() {
    }

    public AnalystQueryEntity(
            UUID id,
            UUID ownerId,
            AnalystRequestType requestType,
            String questionPreview,
            String questionHash,
            AnalystQueryOutcome outcome,
            boolean toolCallBoundReached,
            Instant requestedAt,
            Instant completedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.requestType = requestType;
        this.questionPreview = questionPreview;
        this.questionHash = questionHash;
        this.outcome = outcome;
        this.toolCallBoundReached = toolCallBoundReached;
        this.requestedAt = requestedAt;
        this.completedAt = completedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public AnalystRequestType getRequestType() {
        return requestType;
    }

    public String getQuestionPreview() {
        return questionPreview;
    }

    public String getQuestionHash() {
        return questionHash;
    }

    public AnalystQueryOutcome getOutcome() {
        return outcome;
    }

    public boolean isToolCallBoundReached() {
        return toolCallBoundReached;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setOutcome(AnalystQueryOutcome outcome) {
        this.outcome = outcome;
    }

    public void setToolCallBoundReached(boolean toolCallBoundReached) {
        this.toolCallBoundReached = toolCallBoundReached;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
