package com.minhnb.finvera_be.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.research.domain.DocumentType;
import com.minhnb.finvera_be.research.domain.IngestionStatus;
import com.minhnb.finvera_be.research.domain.ResearchItemType;
import com.minhnb.finvera_be.research.dto.PassageResponse;
import com.minhnb.finvera_be.research.dto.RetrieveFilter;
import com.minhnb.finvera_be.research.dto.RetrieveRequest;
import com.minhnb.finvera_be.research.dto.RetrieveResponse;
import com.minhnb.finvera_be.research.dto.SourceType;
import com.minhnb.finvera_be.research.entity.ResearchChunkEntity;
import com.minhnb.finvera_be.research.entity.ResearchDocumentEntity;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RankedChunkDto;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RetrieveChunksResponse;
import com.minhnb.finvera_be.research.provider.ResearchAiClient;
import com.minhnb.finvera_be.research.repository.NewsArticleRepository;
import com.minhnb.finvera_be.research.repository.ResearchChunkRepository;
import com.minhnb.finvera_be.research.repository.ResearchDocumentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetrievalServiceTests {

    private ResearchAiClient aiClient;
    private ResearchChunkRepository chunkRepository;
    private ResearchDocumentRepository documentRepository;
    private NewsArticleRepository newsRepository;
    private OwnerScopedResearchAccess ownerAccess;
    private RetrievalService service;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        aiClient = mock(ResearchAiClient.class);
        chunkRepository = mock(ResearchChunkRepository.class);
        documentRepository = mock(ResearchDocumentRepository.class);
        newsRepository = mock(NewsArticleRepository.class);
        ownerAccess = mock(OwnerScopedResearchAccess.class);

        when(ownerAccess.getAuthenticatedOwnerId()).thenReturn(ownerId);

        service = new RetrievalService(
                aiClient,
                chunkRepository,
                documentRepository,
                newsRepository,
                ownerAccess);
    }

    @Test
    void retrievePassages_resolvesPostgresContentAndLocation() {
        UUID docId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        UUID vectorId = UUID.randomUUID();

        RankedChunkDto rankedChunk = new RankedChunkDto(
                chunkId, vectorId, 0.85, 0.80, 1.0, 1.0, "DOCUMENT", docId, "FPT", "ANNUAL_REPORT", null, Instant.now());
        when(aiClient.retrieveChunks(any())).thenReturn(new RetrieveChunksResponse(List.of(rankedChunk)));

        ResearchChunkEntity chunk = new ResearchChunkEntity(
                chunkId, ownerId, ResearchItemType.DOCUMENT, docId, null, (short) 0, (short) 15, null,
                "Authoritative text from Postgres", "hash1", vectorId, "gemini-embedding-v1", Instant.now());
        when(chunkRepository.findByIdInAndOwnerId(List.of(chunkId), ownerId)).thenReturn(List.of(chunk));

        ResearchDocumentEntity doc = new ResearchDocumentEntity(
                docId, ownerId, null, "FPT Annual Report 2025", DocumentType.ANNUAL_REPORT, (short) 2025, null,
                "FPT Investor Relations", LocalDate.of(2026, 3, 15), "fpt.pdf", "data".getBytes(), "application/pdf",
                null, IngestionStatus.READY, null, Instant.now(), Instant.now(), "idem-ret");
        when(documentRepository.findByIdAndOwnerId(docId, ownerId)).thenReturn(Optional.of(doc));

        RetrieveRequest request = new RetrieveRequest(
                "What was FPT's revenue?",
                new RetrieveFilter("FPT", DocumentType.ANNUAL_REPORT, null, null, null));

        RetrieveResponse response = service.retrievePassages(request);

        assertThat(response.passages()).hasSize(1);
        PassageResponse passage = response.passages().getFirst();
        assertThat(passage.sourceType()).isEqualTo(SourceType.DOCUMENT);
        assertThat(passage.sourceId()).isEqualTo(docId);
        assertThat(passage.sourceTitle()).isEqualTo("FPT Annual Report 2025");
        assertThat(passage.location()).isEqualTo("Page 15");
        assertThat(passage.excerpt()).isEqualTo("Authoritative text from Postgres");
        assertThat(passage.score()).isEqualTo(0.85);
    }

    @Test
    void retrievePassages_emptyResult_returnsEmptyListTruthfully() {
        when(aiClient.retrieveChunks(any())).thenReturn(new RetrieveChunksResponse(List.of()));

        RetrieveRequest request = new RetrieveRequest("Unknown topic", null);
        RetrieveResponse response = service.retrievePassages(request);

        assertThat(response.passages()).isEmpty();
        assertThat(response.asOf()).isNotNull();
    }

    @Test
    void retrievePassages_aiServiceFailure_throwsRetrievalUnavailableException() {
        when(aiClient.retrieveChunks(any())).thenThrow(new RuntimeException("Connection refused"));

        RetrieveRequest request = new RetrieveRequest("Query during outage", null);

        assertThatThrownBy(() -> service.retrievePassages(request))
                .isInstanceOf(ResearchExceptions.RetrievalUnavailableException.class);
    }
}
