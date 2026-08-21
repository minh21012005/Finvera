package com.minhnb.finvera_be.analyst.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.minhnb.finvera_be.analyst.domain.AnalystQueryOutcome;
import com.minhnb.finvera_be.analyst.domain.AnalystRequestType;
import com.minhnb.finvera_be.analyst.domain.ToolCallStatus;
import com.minhnb.finvera_be.analyst.domain.ToolName;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.*;
import com.minhnb.finvera_be.analyst.provider.AnalystAiClient;
import com.minhnb.finvera_be.research.service.RetrievalService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AnalystService {

    private static final Logger log = LoggerFactory.getLogger(AnalystService.class);

    /** Human-presentable labels for known tool-response field paths (public-api.openapi.yaml's
     * StructuredClaim.sourceField, research R-009). Falls back to a lightly humanized
     * version of the raw fieldPath for anything not listed here. */
    private static final Map<String, String> FIELD_PATH_LABELS = Map.ofEntries(
            Map.entry("price", "Giá"),
            Map.entry("changePercent", "% thay đổi"),
            Map.entry("volume", "Khối lượng"),
            Map.entry("vnIndexValue", "VN-Index"),
            Map.entry("vnIndexChangePercent", "% thay đổi VN-Index"),
            Map.entry("advancers", "Số mã tăng"),
            Map.entry("decliners", "Số mã giảm"),
            Map.entry("signal.direction", "Xu hướng tín hiệu kỹ thuật"),
            Map.entry("eps", "EPS"),
            Map.entry("roe", "ROE"),
            Map.entry("revenueGrowthPercent", "Tăng trưởng doanh thu"),
            Map.entry("peRatio", "P/E"),
            Map.entry("pbRatio", "P/B"),
            Map.entry("classification", "Phân loại định giá"),
            Map.entry("totalValue", "Tổng giá trị danh mục"));

    private final AnalystAiClient aiClient;
    private final AnalystQueryService queryService;
    private final RetrievalService retrievalService;
    private final ObjectMapper objectMapper;

    private final java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    public AnalystService(
            AnalystAiClient aiClient,
            AnalystQueryService queryService,
            RetrievalService retrievalService,
            ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.queryService = queryService;
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
    }

    private static final int ARGUMENT_AUDIT_MAX_LENGTH = 300;

    /**
     * Serializes tool-call arguments for the {@code analyst_tool_call.arguments} jsonb
     * audit column. Two things this MUST do, both found missing in review:
     * (1) produce actual JSON via the ObjectMapper — {@code Map.toString()} (e.g.
     *     {@code {query=...}}) is not valid JSON and jsonb rejects it outright;
     * (2) truncate any free-text argument value (e.g. RESEARCH_RAG/SCREENING's
     *     `query`, which carries the owner's full raw question, up to 2000 chars) to
     *     the same 300-char preview length `question_preview` already uses (AI-002) —
     *     an argument value is still audited, never silently dropped, just not stored
     *     as the full raw question text.
     */
    private String serializeArgumentsForAudit(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "{}";
        }
        Map<String, Object> redacted = new HashMap<>();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s && s.length() > ARGUMENT_AUDIT_MAX_LENGTH) {
                redacted.put(entry.getKey(), s.substring(0, ARGUMENT_AUDIT_MAX_LENGTH));
            } else {
                redacted.put(entry.getKey(), value);
            }
        }
        try {
            return objectMapper.writeValueAsString(redacted);
        } catch (Exception e) {
            log.warn("Failed to serialize tool-call arguments for audit, storing empty object: {}", e.getMessage());
            return "{}";
        }
    }

    private static String humanizeFieldPath(String fieldPath) {
        if (fieldPath == null || fieldPath.isBlank()) {
            return fieldPath;
        }
        String known = FIELD_PATH_LABELS.get(fieldPath);
        if (known != null) {
            return known;
        }
        String last = fieldPath.contains(".") ? fieldPath.substring(fieldPath.lastIndexOf('.') + 1) : fieldPath;
        String spaced = last.replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    public SseEmitter askStream(UUID ownerId, AskAnalystRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }
        if (request.question().length() > 2000) {
            throw new IllegalArgumentException("Question must not exceed 2000 characters");
        }

        SseEmitter emitter = new SseEmitter(60_000L);
        executorService.submit(() -> processAskStream(ownerId, request, emitter));
        return emitter;
    }

    public void processAskStream(UUID ownerId, AskAnalystRequest request, SseEmitter emitter) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }
        if (request.question().length() > 2000) {
            throw new IllegalArgumentException("Question must not exceed 2000 characters");
        }

        UUID queryId = UUID.randomUUID();
        queryService.recordQueryStart(queryId, ownerId, AnalystRequestType.ASK, request.question());
        InternalAskRequest internalRequest = new InternalAskRequest(
                ownerId,
                request.question(),
                request.symbol(),
                request.priorTurns() != null ? request.priorTurns() : List.of());

        Map<Integer, String> toolSeqToName = new HashMap<>();
        List<ToolCallEventDto> recordedToolCalls = new ArrayList<>();

        try {
            aiClient.streamAsk(internalRequest, line -> {
                if (line == null || !line.startsWith("data: ")) {
                    return;
                }
                String json = line.substring(6).trim();
                try {
                    JsonNode node = objectMapper.readTree(json);
                    String type = node.has("type") ? node.get("type").asText() : "";

                    if ("tool_call".equals(type)) {
                        JsonNode toolCallNode = node.get("toolCall");
                        ToolCallEventDto toolCall = objectMapper.treeToValue(toolCallNode, ToolCallEventDto.class);
                        toolSeqToName.put(toolCall.sequenceNo(), toolCall.toolName());
                        recordedToolCalls.add(toolCall);

                        ToolName tName;
                        try {
                            tName = ToolName.valueOf(toolCall.toolName());
                        } catch (Exception e) {
                            tName = ToolName.STOCK;
                        }

                        ToolCallStatus tStatus;
                        try {
                            tStatus = ToolCallStatus.valueOf(toolCall.status());
                        } catch (Exception e) {
                            tStatus = ToolCallStatus.SUCCEEDED;
                        }

                        // Record tool call audit
                        queryService.recordToolCall(
                                queryId,
                                (short) toolCall.sequenceNo(),
                                tName,
                                serializeArgumentsForAudit(toolCall.arguments()),
                                tStatus,
                                toolCall.failureReason(),
                                (int) toolCall.latencyMs(),
                                null);

                        // Relay tool_call verbatim
                        emitter.send(SseEmitter.event().data(json));

                    } else if ("delta".equals(type)) {
                        // Relay delta verbatim
                        emitter.send(SseEmitter.event().data(json));

                    } else if ("final".equals(type)) {
                        JsonNode finalNode = node.get("final");
                        InternalFinalEventDto internalFinal = objectMapper.treeToValue(finalNode, InternalFinalEventDto.class);

                        // Resolve internal structured claims into the public contract shape
                        // (StructuredClaim{claimText, sequenceNo, toolName, sourceField, asOf})
                        // — never the raw fieldPath/claimedValue the model produced internally.
                        List<PublicStructuredClaimDto> publicClaims = new ArrayList<>();
                        if (internalFinal.structuredClaims() != null) {
                            for (InternalStructuredClaimDto internalClaim : internalFinal.structuredClaims()) {
                                String toolName = toolSeqToName.getOrDefault(internalClaim.sequenceNo(), "UNKNOWN");
                                publicClaims.add(new PublicStructuredClaimDto(
                                        internalClaim.claimText(),
                                        internalClaim.sequenceNo(),
                                        toolName,
                                        humanizeFieldPath(internalClaim.fieldPath()),
                                        internalClaim.asOf()));
                            }
                        }

                        // Resolve internal document claims (bare chunkId) into the public
                        // Citation shape, identical to Feature 006's AskService.java —
                        // never pass the internal DocumentClaimDto through unchanged.
                        List<PublicDocumentClaimDto> publicDocumentClaims = new ArrayList<>();
                        if (internalFinal.documentClaims() != null) {
                            for (DocumentClaimDto internalDoc : internalFinal.documentClaims()) {
                                if (internalDoc.chunkId() == null || internalDoc.chunkId().isBlank()) {
                                    continue;
                                }
                                try {
                                    UUID chunkId = UUID.fromString(internalDoc.chunkId());
                                    retrievalService.resolveChunkCitation(chunkId, ownerId).ifPresent(passage ->
                                            publicDocumentClaims.add(new PublicDocumentClaimDto(
                                                    internalDoc.claimText(),
                                                    passage.sourceType().name(),
                                                    passage.sourceId().toString(),
                                                    passage.sourceTitle(),
                                                    passage.location(),
                                                    passage.source())));
                                } catch (IllegalArgumentException e) {
                                    log.warn("Analyst document claim carried an unparseable chunkId, dropped: {}", internalDoc.chunkId());
                                }
                            }
                        }

                        PublicFinalEventDto publicFinal = new PublicFinalEventDto(
                                internalFinal.answer(),
                                publicClaims,
                                publicDocumentClaims,
                                internalFinal.refused(),
                                recordedToolCalls,
                                internalFinal.toolCallBoundReached(),
                                internalFinal.ruleVersion() != null ? internalFinal.ruleVersion() : "orchestration-v1");

                        Map<String, Object> publicFinalEnvelope = Map.of(
                                "type", "final",
                                "final", publicFinal);

                        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(publicFinalEnvelope)));

                        AnalystQueryOutcome outcome = publicFinal.refused()
                                ? AnalystQueryOutcome.REFUSED
                                : (publicFinal.toolCallBoundReached() ? AnalystQueryOutcome.PARTIAL : AnalystQueryOutcome.COMPLETED);

                        // Record query completion
                        queryService.recordQueryCompletion(
                                queryId,
                                outcome,
                                publicFinal.toolCallBoundReached());
                    }
                } catch (IOException e) {
                    log.error("Failed to parse or relay SSE event: {}", json, e);
                }
            });

            emitter.complete();
        } catch (Exception e) {
            log.error("Error processing analyst ask stream for queryId {}", queryId, e);
            emitter.completeWithError(e);
        }
    }

    public com.minhnb.finvera_be.analyst.dto.AskAnalystDto.ExplainResponse explainOutput(
            UUID ownerId,
            com.minhnb.finvera_be.analyst.dto.AskAnalystDto.ExplainRequest request) {
        if (request == null || request.outputType() == null || request.outputType().isBlank()) {
            throw new IllegalArgumentException("outputType must not be blank");
        }
        if (request.evidenceFactors() == null || request.evidenceFactors().isEmpty()) {
            throw new IllegalArgumentException("evidenceFactors must contain at least 1 item");
        }

        UUID queryId = UUID.randomUUID();
        String summary = "Explain " + request.outputType() + (request.symbol() != null ? " (" + request.symbol() + ")" : "");
        queryService.recordQueryStart(queryId, ownerId, AnalystRequestType.EXPLAIN, summary);

        try {
            var internalRequest = new com.minhnb.finvera_be.analyst.dto.AskAnalystDto.InternalExplainRequest(
                    ownerId,
                    request.outputType(),
                    request.symbol(),
                    request.evidenceFactors());

            var result = aiClient.explain(internalRequest);
            if (result == null) {
                queryService.recordQueryCompletion(queryId, AnalystQueryOutcome.FAILED, false);
                return new com.minhnb.finvera_be.analyst.dto.AskAnalystDto.ExplainResponse(
                        "Không thể tạo giải thích tự động.",
                        false);
            }

            AnalystQueryOutcome outcome = result.verified() ? AnalystQueryOutcome.COMPLETED : AnalystQueryOutcome.REFUSED;
            queryService.recordQueryCompletion(queryId, outcome, false);

            return new com.minhnb.finvera_be.analyst.dto.AskAnalystDto.ExplainResponse(
                    result.explanation(),
                    result.verified());
        } catch (Exception e) {
            log.error("Error during explain output for queryId {}", queryId, e);
            queryService.recordQueryCompletion(queryId, AnalystQueryOutcome.FAILED, false);
            throw new RuntimeException("Explain service failed", e);
        }
    }
}
