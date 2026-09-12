package com.minhnb.finvera_be.analyst.entity;

import com.minhnb.finvera_be.analyst.domain.ConversationExchangeStatus;
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
@Table(name = "analyst_conversation_exchange")
public class AnalystConversationExchangeEntity {
    @Id private UUID id;
    @Column(name = "conversation_id", nullable = false) private UUID conversationId;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(name = "sequence_no", nullable = false) private long sequenceNo;
    @Column(name = "client_request_id", nullable = false) private UUID clientRequestId;
    @Column(nullable = false, length = 2000) private String question;
    @Column(length = 20) private String symbol;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ConversationExchangeStatus status;
    @Column(columnDefinition = "text") private String answer;
    @Column(name = "response_schema_version", length = 40) private String responseSchemaVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_metadata", columnDefinition = "jsonb") private String responseMetadata;
    @Column(name = "failure_code", length = 64) private String failureCode;
    @Column(name = "context_rule_version", nullable = false, length = 40) private String contextRuleVersion;
    @Column(name = "context_included_count", nullable = false) private short contextIncludedCount;
    @Column(name = "context_omitted_count", nullable = false) private int contextOmittedCount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected AnalystConversationExchangeEntity() { }

    public AnalystConversationExchangeEntity(UUID id, UUID conversationId, UUID ownerId, long sequenceNo,
            UUID clientRequestId, String question, String symbol, short included, int omitted, Instant now) {
        this.id=id; this.conversationId=conversationId; this.ownerId=ownerId; this.sequenceNo=sequenceNo;
        this.clientRequestId=clientRequestId; this.question=question; this.symbol=symbol;
        this.status=ConversationExchangeStatus.PROCESSING; this.contextRuleVersion="context-window-v1";
        this.contextIncludedCount=included; this.contextOmittedCount=omitted; this.createdAt=now;
    }

    public UUID getId(){return id;} public UUID getConversationId(){return conversationId;}
    public UUID getOwnerId(){return ownerId;} public long getSequenceNo(){return sequenceNo;}
    public UUID getClientRequestId(){return clientRequestId;} public String getQuestion(){return question;}
    public String getSymbol(){return symbol;} public ConversationExchangeStatus getStatus(){return status;}
    public String getAnswer(){return answer;} public String getResponseMetadata(){return responseMetadata;}
    public String getFailureCode(){return failureCode;} public String getContextRuleVersion(){return contextRuleVersion;}
    public short getContextIncludedCount(){return contextIncludedCount;} public int getContextOmittedCount(){return contextOmittedCount;}
    public Instant getCreatedAt(){return createdAt;} public Instant getCompletedAt(){return completedAt;}

    public boolean complete(String answer, String metadata, Instant now) {
        if (status != ConversationExchangeStatus.PROCESSING) return false;
        this.answer=answer; this.responseMetadata=metadata; this.responseSchemaVersion="analyst-conversation-answer-v1";
        this.status=ConversationExchangeStatus.COMPLETED; this.completedAt=now;
        return true;
    }
    public boolean fail(String code, ConversationExchangeStatus terminal, Instant now) {
        if (status != ConversationExchangeStatus.PROCESSING) return false;
        this.failureCode=code; this.status=terminal; this.completedAt=now;
        return true;
    }
}
