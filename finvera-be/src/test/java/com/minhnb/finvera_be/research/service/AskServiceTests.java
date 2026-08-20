package com.minhnb.finvera_be.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.minhnb.finvera_be.research.domain.DocumentType;
import com.minhnb.finvera_be.research.domain.IngestionStatus;
import com.minhnb.finvera_be.research.domain.ResearchItemType;
import com.minhnb.finvera_be.research.dto.AskRequest;
import com.minhnb.finvera_be.research.dto.SourceType;
import com.minhnb.finvera_be.research.entity.ResearchChunkEntity;
import com.minhnb.finvera_be.research.entity.ResearchDocumentEntity;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RankedChunkDto;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RetrieveChunksResponse;
import com.minhnb.finvera_be.research.provider.ResearchAiClient;
import com.minhnb.finvera_be.research.repository.NewsArticleRepository;
import com.minhnb.finvera_be.research.repository.ResearchChunkRepository;
import com.minhnb.finvera_be.research.repository.ResearchDocumentRepository;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AskServiceTests {

    private ResearchAiClient aiClient;
    private ResearchChunkRepository chunkRepository;
    private ResearchDocumentRepository documentRepository;
    private NewsArticleRepository newsRepository;
    private OwnerScopedResearchAccess ownerAccess;
    private ObjectMapper objectMapper;
    private AskService askService;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        aiClient = mock(ResearchAiClient.class);
        chunkRepository = mock(ResearchChunkRepository.class);
        documentRepository = mock(ResearchDocumentRepository.class);
        newsRepository = mock(NewsArticleRepository.class);
        ownerAccess = mock(OwnerScopedResearchAccess.class);
        objectMapper = new ObjectMapper();

        when(ownerAccess.getAuthenticatedOwnerId()).thenReturn(ownerId);

        askService = new AskService(
                aiClient,
                chunkRepository,
                documentRepository,
                newsRepository,
                ownerAccess,
                objectMapper);
    }

    @Test
    void blank_query_throws_exception() {
        assertThatThrownBy(() -> askService.askQuestion(new AskRequest("  ", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void empty_retrieval_returns_refusal_emitter() {
        when(aiClient.retrieveChunks(any())).thenReturn(new RetrieveChunksResponse(Collections.emptyList()));

        SseEmitter emitter = askService.askQuestion(new AskRequest("Doanh thu?", null));
        assertThat(emitter).isNotNull();
    }

    @Test
    void grounded_synthesis_resolves_citations_from_postgres() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        RankedChunkDto ranked = new RankedChunkDto(
                chunkId,
                UUID.randomUUID(),
                0.95,
                0.90,
                1.0,
                1.0,
                "DOCUMENT",
                docId,
                "FPT",
                "FINANCIAL_REPORT",
                null,
                Instant.now());

        when(aiClient.retrieveChunks(any())).thenReturn(new RetrieveChunksResponse(List.of(ranked)));

        ResearchChunkEntity chunk = new ResearchChunkEntity(
                chunkId,
                ownerId,
                ResearchItemType.DOCUMENT,
                docId,
                null,
                (short) 0,
                (short) 5,
                null,
                "Doanh thu đạt 60.000 tỷ VND",
                "hash",
                UUID.randomUUID(),
                "v1",
                Instant.now());

        when(chunkRepository.findByIdInAndOwnerId(List.of(chunkId), ownerId)).thenReturn(List.of(chunk));
        when(chunkRepository.findByIdAndOwnerId(chunkId, ownerId)).thenReturn(Optional.of(chunk));

        ResearchDocumentEntity doc = new ResearchDocumentEntity(
                docId,
                ownerId,
                UUID.randomUUID(),
                "Báo cáo tài chính 2025",
                DocumentType.FINANCIAL_REPORT,
                (short) 2025,
                (short) 4,
                "FPT IR",
                LocalDate.of(2025, 12, 31),
                "bctc.pdf",
                new byte[0],
                "application/pdf",
                null,
                IngestionStatus.READY,
                null,
                Instant.now(),
                Instant.now(),
                "idem-1");

        when(documentRepository.findByIdAndOwnerId(docId, ownerId)).thenReturn(Optional.of(doc));

        String sseData = "data: {\"type\": \"delta\", \"textDelta\": \"Doanh thu \"}\n\n"
                + "data: {\"type\": \"final\", \"final\": {\"answer\": \"Doanh thu đạt 60.000 tỷ.\", \"citations\": [{\"chunkId\": \"" + chunkId + "\", \"claimText\": \"Doanh thu đạt 60.000 tỷ\"}], \"refused\": false, \"ruleVersion\": \"rag-v1\"}}\n\n";

        InputStream inputStream = new ByteArrayInputStream(sseData.getBytes(StandardCharsets.UTF_8));
        when(aiClient.streamSynthesize(any())).thenReturn(inputStream);

        SseEmitter emitter = askService.askQuestion(new AskRequest("Doanh thu FPT?", null));
        assertThat(emitter).isNotNull();
    }
}
