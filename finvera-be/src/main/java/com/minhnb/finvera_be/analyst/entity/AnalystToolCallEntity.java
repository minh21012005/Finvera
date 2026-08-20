package com.minhnb.finvera_be.analyst.entity;

import com.minhnb.finvera_be.analyst.domain.ToolCallStatus;
import com.minhnb.finvera_be.analyst.domain.ToolName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analyst_tool_call")
public class AnalystToolCallEntity {

    @Id
    private UUID id;

    @Column(name = "analyst_query_id", nullable = false)
    private UUID analystQueryId;

    @Column(name = "sequence_no", nullable = false)
    private short sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_name", nullable = false, length = 32)
    private ToolName toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "arguments", nullable = false, columnDefinition = "jsonb")
    private String arguments;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ToolCallStatus status;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "called_at", nullable = false)
    private Instant calledAt;

    protected AnalystToolCallEntity() {
    }

    public AnalystToolCallEntity(
            UUID id,
            UUID analystQueryId,
            short sequenceNo,
            ToolName toolName,
            String arguments,
            ToolCallStatus status,
            String failureReason,
            int latencyMs,
            Instant calledAt) {
        this.id = id;
        this.analystQueryId = analystQueryId;
        this.sequenceNo = sequenceNo;
        this.toolName = toolName;
        this.arguments = arguments;
        this.status = status;
        this.failureReason = failureReason;
        this.latencyMs = latencyMs;
        this.calledAt = calledAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAnalystQueryId() {
        return analystQueryId;
    }

    public short getSequenceNo() {
        return sequenceNo;
    }

    public ToolName getToolName() {
        return toolName;
    }

    public String getArguments() {
        return arguments;
    }

    public ToolCallStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public Instant getCalledAt() {
        return calledAt;
    }
}
