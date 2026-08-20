package com.minhnb.finvera_be.analyst.service;

import com.minhnb.finvera_be.analyst.domain.AnalystQueryOutcome;
import com.minhnb.finvera_be.analyst.domain.AnalystRequestType;
import com.minhnb.finvera_be.analyst.domain.ToolCallStatus;
import com.minhnb.finvera_be.analyst.domain.ToolName;
import com.minhnb.finvera_be.analyst.entity.AnalystQueryEntity;
import com.minhnb.finvera_be.analyst.entity.AnalystToolCallEntity;
import com.minhnb.finvera_be.analyst.repository.AnalystQueryRepository;
import com.minhnb.finvera_be.analyst.repository.AnalystToolCallRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for audit-logging analyst queries and tool invocations (AI-002, DATA-001, SEC-003).
 * Never persists full conversational state or raw prompt/response text.
 */
@Service
public class AnalystQueryService {

    private final AnalystQueryRepository queryRepository;
    private final AnalystToolCallRepository toolCallRepository;
    private final Clock clock;

    public AnalystQueryService(
            AnalystQueryRepository queryRepository,
            AnalystToolCallRepository toolCallRepository,
            Clock clock) {
        this.queryRepository = queryRepository;
        this.toolCallRepository = toolCallRepository;
        this.clock = clock;
    }

    @Transactional
    public AnalystQueryEntity recordQueryStart(
            UUID queryId,
            UUID ownerId,
            AnalystRequestType requestType,
            String rawQuestion) {
        Objects.requireNonNull(queryId, "queryId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(requestType, "requestType");
        String question = rawQuestion != null ? rawQuestion : "";

        String preview = question.length() <= 300 ? question : question.substring(0, 300);
        String hash = sha256Hex(question);
        Instant now = clock.instant();

        AnalystQueryEntity entity = new AnalystQueryEntity(
                queryId,
                ownerId,
                requestType,
                preview,
                hash,
                AnalystQueryOutcome.PARTIAL,
                false,
                now,
                null);

        return queryRepository.save(entity);
    }

    @Transactional
    public AnalystToolCallEntity recordToolCall(
            UUID queryId,
            short sequenceNo,
            ToolName toolName,
            String argumentsJson,
            ToolCallStatus status,
            String failureReason,
            int latencyMs,
            Instant calledAt) {
        Objects.requireNonNull(queryId, "queryId");
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(status, "status");
        Instant timestamp = calledAt != null ? calledAt : clock.instant();
        String safeArgs = argumentsJson != null && !argumentsJson.isBlank() ? argumentsJson : "{}";

        AnalystToolCallEntity entity = new AnalystToolCallEntity(
                UUID.randomUUID(),
                queryId,
                sequenceNo,
                toolName,
                safeArgs,
                status,
                failureReason,
                latencyMs,
                timestamp);

        return toolCallRepository.save(entity);
    }

    @Transactional
    public void recordQueryCompletion(
            UUID queryId,
            AnalystQueryOutcome outcome,
            boolean toolCallBoundReached) {
        Objects.requireNonNull(queryId, "queryId");
        Objects.requireNonNull(outcome, "outcome");

        queryRepository.findById(queryId).ifPresent(entity -> {
            entity.setOutcome(outcome);
            entity.setToolCallBoundReached(toolCallBoundReached);
            entity.setCompletedAt(clock.instant());
            queryRepository.save(entity);
        });
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
