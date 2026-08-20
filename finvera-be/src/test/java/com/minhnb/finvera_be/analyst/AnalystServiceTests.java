package com.minhnb.finvera_be.analyst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.minhnb.finvera_be.analyst.domain.AnalystQueryOutcome;
import com.minhnb.finvera_be.analyst.domain.AnalystRequestType;
import com.minhnb.finvera_be.analyst.domain.ToolCallStatus;
import com.minhnb.finvera_be.analyst.domain.ToolName;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.*;
import com.minhnb.finvera_be.analyst.provider.AnalystAiClient;
import com.minhnb.finvera_be.analyst.service.AnalystQueryService;
import com.minhnb.finvera_be.analyst.service.AnalystService;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AnalystServiceTests {

    private AnalystAiClient aiClient;
    private AnalystQueryService queryService;
    private ObjectMapper objectMapper;
    private AnalystService analystService;

    @BeforeEach
    void setUp() {
        aiClient = mock(AnalystAiClient.class);
        queryService = mock(AnalystQueryService.class);
        objectMapper = new ObjectMapper();
        analystService = new AnalystService(aiClient, queryService, objectMapper);
    }

    @Test
    void processAskStream_blankQuestion_throwsException() {
        SseEmitter emitter = new SseEmitter();
        UUID ownerId = UUID.randomUUID();
        AskAnalystRequest req = new AskAnalystRequest("   ", "HPG", List.of());

        assertThatThrownBy(() -> analystService.processAskStream(ownerId, req, emitter))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Question must not be blank");
    }

    @Test
    void processAskStream_resolvesInternalClaimsToPublicClaimsWithExactAsOf() {
        UUID ownerId = UUID.randomUUID();

        String toolCallJson = "data: {\"type\":\"tool_call\",\"toolCall\":{\"sequenceNo\":1,\"toolName\":\"STOCK\",\"arguments\":{\"symbol\":\"HPG\"},\"status\":\"SUCCEEDED\",\"failureReason\":null,\"latencyMs\":120}}";
        String deltaJson = "data: {\"type\":\"delta\",\"textDelta\":\"Giá HPG là 28500\"}";
        String finalJson = "data: {\"type\":\"final\",\"final\":{\"answer\":\"Giá HPG là 28500\",\"structuredClaims\":[{\"claimText\":\"Giá 28500\",\"sequenceNo\":1,\"fieldPath\":\"price\",\"claimedValue\":\"28500\",\"asOf\":\"2026-08-20T10:00:00Z\"}],\"documentClaims\":[],\"refused\":false,\"toolCalls\":[],\"toolCallBoundReached\":false,\"ruleVersion\":\"orchestration-v1\"}}";

        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept(toolCallJson);
            consumer.accept(deltaJson);
            consumer.accept(finalJson);
            return null;
        }).when(aiClient).streamAsk(any(), any());

        SseEmitter emitter = mock(SseEmitter.class);
        AskAnalystRequest req = new AskAnalystRequest("Giá HPG hôm nay", "HPG", Collections.emptyList());

        analystService.processAskStream(ownerId, req, emitter);

        // Verify audit logging
        verify(queryService).recordQueryStart(any(UUID.class), eq(ownerId), eq(AnalystRequestType.ASK), eq("Giá HPG hôm nay"));
        verify(queryService).recordToolCall(any(UUID.class), eq((short) 1), eq(ToolName.STOCK), anyString(), eq(ToolCallStatus.SUCCEEDED), any(), eq(120), any());
        verify(queryService).recordQueryCompletion(any(UUID.class), eq(AnalystQueryOutcome.COMPLETED), eq(false));
    }
}
