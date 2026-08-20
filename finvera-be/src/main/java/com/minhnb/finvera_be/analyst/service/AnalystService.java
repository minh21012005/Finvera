package com.minhnb.finvera_be.analyst.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.minhnb.finvera_be.analyst.domain.AnalystQueryOutcome;
import com.minhnb.finvera_be.analyst.domain.AnalystRequestType;
import com.minhnb.finvera_be.analyst.domain.ToolCallStatus;
import com.minhnb.finvera_be.analyst.domain.ToolName;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.*;
import com.minhnb.finvera_be.analyst.provider.AnalystAiClient;
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

    private final AnalystAiClient aiClient;
    private final AnalystQueryService queryService;
    private final ObjectMapper objectMapper;

    private final java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    public AnalystService(AnalystAiClient aiClient, AnalystQueryService queryService, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
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
                                toolCall.arguments() != null ? toolCall.arguments().toString() : "{}",
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

                        // T021: Resolve internal structured claims into public format
                        List<PublicStructuredClaimDto> publicClaims = new ArrayList<>();
                        if (internalFinal.structuredClaims() != null) {
                            for (InternalStructuredClaimDto internalClaim : internalFinal.structuredClaims()) {
                                String toolName = toolSeqToName.getOrDefault(internalClaim.sequenceNo(), "UNKNOWN");
                                publicClaims.add(new PublicStructuredClaimDto(
                                        internalClaim.claimText(),
                                        toolName,
                                        internalClaim.fieldPath(),
                                        internalClaim.claimedValue(),
                                        internalClaim.asOf()));
                            }
                        }

                        PublicFinalEventDto publicFinal = new PublicFinalEventDto(
                                internalFinal.answer(),
                                publicClaims,
                                internalFinal.documentClaims() != null ? internalFinal.documentClaims() : List.of(),
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
}
