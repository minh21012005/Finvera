package com.minhnb.finvera_be.analyst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
import com.minhnb.finvera_be.research.dto.PassageResponse;
import com.minhnb.finvera_be.research.dto.SourceType;
import com.minhnb.finvera_be.research.service.RetrievalService;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AnalystServiceTests {

    private AnalystAiClient aiClient;
    private AnalystQueryService queryService;
    private RetrievalService retrievalService;
    private ObjectMapper objectMapper;
    private AnalystService analystService;

    @BeforeEach
    void setUp() {
        aiClient = mock(AnalystAiClient.class);
        queryService = mock(AnalystQueryService.class);
        retrievalService = mock(RetrievalService.class);
        objectMapper = new ObjectMapper();
        analystService = new AnalystService(aiClient, queryService, retrievalService, objectMapper);
    }

    /** Captures every payload actually sent through a mocked SseEmitter, decoding each
     * SseEventBuilder to its raw data string — the only way to verify the real
     * public-contract JSON shape rather than just that "something" was sent. */
    private static List<String> capturedSseData(SseEmitter emitter) throws Exception {
        // SseEmitter overloads send(Object) and send(SseEventBuilder) separately; every
        // call site in AnalystService resolves to the SseEventBuilder overload at
        // compile time (SseEmitter.event().data(...) returns that type), so the captor
        // must target it specifically or Mockito verifies the wrong overload entirely.
        ArgumentCaptor<SseEmitter.SseEventBuilder> captor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter, atLeastOnce()).send(captor.capture());
        List<String> out = new java.util.ArrayList<>();
        for (SseEmitter.SseEventBuilder builder : captor.getAllValues()) {
            for (DataWithMediaType part : builder.build()) {
                if (part.getData() instanceof String s) {
                    out.add(s);
                }
            }
        }
        return out;
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
    void processAskStream_resolvesInternalClaimsToPublicClaimsWithExactAsOf() throws Exception {
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

        // Verify the ACTUAL public final-event JSON sent to the browser — not merely
        // that "a" response was produced. This is the regression test for the bug
        // found in review: the public shape must carry sequenceNo (contract-required)
        // and a human sourceField label, never the raw internal fieldPath/claimedValue.
        String sentFinal = capturedSseData(emitter).stream()
                .filter(s -> s.contains("\"type\":\"final\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no final event was sent"));

        var finalNode = objectMapper.readTree(sentFinal).get("final");
        var claim = finalNode.get("structuredClaims").get(0);
        assertThat(claim.get("sequenceNo").asInt()).isEqualTo(1);
        assertThat(claim.get("toolName").asString()).isEqualTo("STOCK");
        assertThat(claim.get("sourceField").asString()).isEqualTo("Giá");
        assertThat(claim.get("asOf").asString()).isEqualTo("2026-08-20T10:00:00Z");
        assertThat(claim.has("fieldPath")).isFalse();
        assertThat(claim.has("claimedValue")).isFalse();
    }

    @Test
    void processAskStream_resolvesDocumentClaimChunkIdToPublicCitationShape() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        when(retrievalService.resolveChunkCitation(chunkId, ownerId)).thenReturn(Optional.of(new PassageResponse(
                chunkId,
                SourceType.DOCUMENT,
                docId,
                "Báo cáo thường niên HPG 2025",
                "Page 12",
                "HPG Investor Relations",
                LocalDate.of(2025, 12, 31),
                "Kế hoạch doanh thu 2026",
                0.9)));

        String finalJson = "data: {\"type\":\"final\",\"final\":{\"answer\":\"Theo tài liệu, kế hoạch doanh thu 2026.\","
                + "\"structuredClaims\":[],"
                + "\"documentClaims\":[{\"chunkId\":\"" + chunkId + "\",\"claimText\":\"Kế hoạch doanh thu 2026\"}],"
                + "\"refused\":false,\"toolCalls\":[],\"toolCallBoundReached\":false,\"ruleVersion\":\"orchestration-v1\"}}";

        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept(finalJson);
            return null;
        }).when(aiClient).streamAsk(any(), any());

        SseEmitter emitter = mock(SseEmitter.class);
        AskAnalystRequest req = new AskAnalystRequest("Kế hoạch doanh thu HPG?", "HPG", Collections.emptyList());

        analystService.processAskStream(ownerId, req, emitter);

        String sentFinal = capturedSseData(emitter).stream()
                .filter(s -> s.contains("\"type\":\"final\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no final event was sent"));

        var docClaim = objectMapper.readTree(sentFinal).get("final").get("documentClaims").get(0);
        assertThat(docClaim.get("sourceType").asString()).isEqualTo("DOCUMENT");
        assertThat(docClaim.get("sourceId").asString()).isEqualTo(docId.toString());
        assertThat(docClaim.get("sourceTitle").asString()).isEqualTo("Báo cáo thường niên HPG 2025");
        assertThat(docClaim.get("location").asString()).isEqualTo("Page 12");
        assertThat(docClaim.get("source").asString()).isEqualTo("HPG Investor Relations");
        assertThat(docClaim.has("chunkId")).isFalse();
    }

    @Test
    void processAskStream_serializesToolCallArgumentsAsValidJsonAndTruncatesLongValues() throws Exception {
        // Bug found in review: toolCall.arguments().toString() (Java's Map.toString,
        // e.g. "{query=...}") is not valid JSON and the jsonb column rejects it
        // outright; separately, AI-002 requires a long free-text argument (e.g.
        // RESEARCH_RAG's `query`, up to 2000 chars of the owner's raw question) to be
        // truncated before persisting, mirroring question_preview's own 300-char rule.
        UUID ownerId = UUID.randomUUID();
        String longQuery = "a".repeat(2000);

        String toolCallJson = "data: {\"type\":\"tool_call\",\"toolCall\":{\"sequenceNo\":1,\"toolName\":\"RESEARCH_RAG\","
                + "\"arguments\":{\"query\":\"" + longQuery + "\",\"symbol\":\"HPG\"},"
                + "\"status\":\"SUCCEEDED\",\"failureReason\":null,\"latencyMs\":90}}";

        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept(toolCallJson);
            return null;
        }).when(aiClient).streamAsk(any(), any());

        SseEmitter emitter = mock(SseEmitter.class);
        AskAnalystRequest req = new AskAnalystRequest("Tài liệu HPG?", "HPG", Collections.emptyList());

        analystService.processAskStream(ownerId, req, emitter);

        ArgumentCaptor<String> argsCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryService).recordToolCall(
                any(UUID.class), eq((short) 1), eq(ToolName.RESEARCH_RAG), argsCaptor.capture(),
                eq(ToolCallStatus.SUCCEEDED), any(), eq(90), any());

        String persistedArgs = argsCaptor.getValue();
        var parsed = objectMapper.readTree(persistedArgs); // throws if not valid JSON
        assertThat(parsed.get("query").asString().length()).isEqualTo(300);
        assertThat(parsed.get("symbol").asString()).isEqualTo("HPG");
    }

    @Test
    void explainOutput_blankOutputType_throwsException() {
        UUID ownerId = UUID.randomUUID();
        ExplainRequest req = new ExplainRequest("  ", "HPG", List.of(new EvidenceFactorDto("RSI", "RSI 70")));

        assertThatThrownBy(() -> analystService.explainOutput(ownerId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outputType must not be blank");
    }

    @Test
    void explainOutput_emptyEvidenceFactors_throwsException() {
        UUID ownerId = UUID.randomUUID();
        ExplainRequest req = new ExplainRequest("SIGNAL", "HPG", List.of());

        assertThatThrownBy(() -> analystService.explainOutput(ownerId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceFactors must contain at least 1 item");
    }

    @Test
    void explainOutput_successfulVerification_recordsCompletedAuditAndReturnsResponse() {
        UUID ownerId = UUID.randomUUID();
        ExplainRequest req = new ExplainRequest(
                "SIGNAL",
                "HPG",
                List.of(new EvidenceFactorDto("RSI_14", "RSI 70")));

        when(aiClient.explain(any())).thenReturn(new InternalExplainResult(
                "Tín hiệu mua kỹ thuật",
                List.of("RSI_14"),
                true,
                "orchestration-v1"));

        ExplainResponse response = analystService.explainOutput(ownerId, req);

        assertThat(response.verified()).isTrue();
        assertThat(response.explanation()).isEqualTo("Tín hiệu mua kỹ thuật");
        verify(queryService).recordQueryStart(any(UUID.class), eq(ownerId), eq(AnalystRequestType.EXPLAIN), eq("Explain SIGNAL (HPG)"));
        verify(queryService).recordQueryCompletion(any(UUID.class), eq(AnalystQueryOutcome.COMPLETED), eq(false));
    }

    @Test
    void explainOutput_unverified_recordsRefusedAuditAndReturnsResponse() {
        UUID ownerId = UUID.randomUUID();
        ExplainRequest req = new ExplainRequest(
                "VALUATION_CLASSIFICATION",
                null,
                List.of(new EvidenceFactorDto("PE", "PE 12")));

        when(aiClient.explain(any())).thenReturn(new InternalExplainResult(
                "Hiện chưa có sẵn phần giải thích tự động.",
                List.of(),
                false,
                "orchestration-v1"));

        ExplainResponse response = analystService.explainOutput(ownerId, req);

        assertThat(response.verified()).isFalse();
        assertThat(response.explanation()).contains("Hiện chưa có sẵn");
        verify(queryService).recordQueryStart(any(UUID.class), eq(ownerId), eq(AnalystRequestType.EXPLAIN), eq("Explain VALUATION_CLASSIFICATION"));
        verify(queryService).recordQueryCompletion(any(UUID.class), eq(AnalystQueryOutcome.REFUSED), eq(false));
    }

    @Test
    void explainOutput_aiClientException_recordsFailedAuditAndRethrows() {
        UUID ownerId = UUID.randomUUID();
        ExplainRequest req = new ExplainRequest(
                "RISK_FACTOR",
                "VND",
                List.of(new EvidenceFactorDto("BETA", "Beta 1.5")));

        when(aiClient.explain(any())).thenThrow(new RuntimeException("AI service timeout"));

        assertThatThrownBy(() -> analystService.explainOutput(ownerId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Explain service failed");

        verify(queryService).recordQueryStart(any(UUID.class), eq(ownerId), eq(AnalystRequestType.EXPLAIN), eq("Explain RISK_FACTOR (VND)"));
        verify(queryService).recordQueryCompletion(any(UUID.class), eq(AnalystQueryOutcome.FAILED), eq(false));
    }
}
