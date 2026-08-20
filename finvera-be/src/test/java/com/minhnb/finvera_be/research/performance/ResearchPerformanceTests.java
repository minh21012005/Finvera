package com.minhnb.finvera_be.research.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.research.domain.DocumentType;
import com.minhnb.finvera_be.research.dto.RetrieveRequest;
import com.minhnb.finvera_be.research.dto.SubmitDocumentCommand;
import com.minhnb.finvera_be.research.dto.SubmitNewsArticleCommand;
import com.minhnb.finvera_be.research.entity.ResearchDocumentEntity;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RankedChunkDto;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RetrieveChunksResponse;
import com.minhnb.finvera_be.research.provider.ResearchAiClient;
import com.minhnb.finvera_be.research.repository.NewsArticleRepository;
import com.minhnb.finvera_be.research.repository.ResearchChunkRepository;
import com.minhnb.finvera_be.research.repository.ResearchDocumentRepository;
import com.minhnb.finvera_be.research.service.NewsArticleService;
import com.minhnb.finvera_be.research.service.OwnerScopedResearchAccess;
import com.minhnb.finvera_be.research.service.ResearchDocumentService;
import com.minhnb.finvera_be.research.service.RetrievalService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResearchPerformanceTests {

    private ResearchAiClient aiClient;
    private ResearchDocumentRepository documentRepository;
    private NewsArticleRepository newsRepository;
    private ResearchChunkRepository chunkRepository;
    private MarketReferenceDataService marketReferenceData;
    private OwnerScopedResearchAccess ownerAccess;

    private ResearchDocumentService documentService;
    private NewsArticleService newsService;
    private RetrievalService retrievalService;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        aiClient = mock(ResearchAiClient.class);
        documentRepository = mock(ResearchDocumentRepository.class);
        newsRepository = mock(NewsArticleRepository.class);
        chunkRepository = mock(ResearchChunkRepository.class);
        marketReferenceData = mock(MarketReferenceDataService.class);
        ownerAccess = mock(OwnerScopedResearchAccess.class);

        when(ownerAccess.getAuthenticatedOwnerId()).thenReturn(ownerId);

        documentService = new ResearchDocumentService(
                documentRepository,
                chunkRepository,
                marketReferenceData,
                aiClient,
                ownerAccess);

        newsService = new NewsArticleService(
                newsRepository,
                chunkRepository,
                marketReferenceData,
                aiClient,
                ownerAccess);

        retrievalService = new RetrievalService(
                aiClient,
                chunkRepository,
                documentRepository,
                newsRepository,
                ownerAccess);
    }

    @Test
    void submit_document_is_asynchronous_and_returns_under_100ms() {
        SubmitDocumentCommand command = new SubmitDocumentCommand(
                "BCTC Q4",
                "FPT",
                DocumentType.QUARTERLY_REPORT,
                2025,
                4,
                "FPT Corp",
                LocalDate.now(),
                new byte[]{1, 2, 3},
                "file.pdf",
                null,
                "perf-idem-1");

        when(documentRepository.findByOwnerIdAndIdempotencyKey(ownerId, "perf-idem-1")).thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        long start = System.nanoTime();
        var response = documentService.submitDocument(command);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(response).isNotNull();
        assertThat(elapsedMs).isLessThan(200); // Async dispatch responds immediately
    }

    @Test
    void submit_news_article_is_asynchronous_and_returns_under_100ms() {
        SubmitNewsArticleCommand command = new SubmitNewsArticleCommand(
                "Tin tức vĩ mô",
                null,
                "SBV",
                null,
                Instant.now(),
                "Nội dung bài viết",
                "perf-idem-2");

        when(newsRepository.findByOwnerIdAndIdempotencyKey(ownerId, "perf-idem-2")).thenReturn(Optional.empty());
        when(newsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        long start = System.nanoTime();
        var response = newsService.submitNewsArticle(command);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(response).isNotNull();
        assertThat(elapsedMs).isLessThan(200);
    }

    @Test
    void retrieval_pipeline_resolves_and_returns_under_200ms() {
        UUID chunkId = UUID.randomUUID();
        when(aiClient.retrieveChunks(any())).thenReturn(new RetrieveChunksResponse(
                List.of(new RankedChunkDto(chunkId, UUID.randomUUID(), 0.95, 0.9, 0.8, 1.0, "DOCUMENT", UUID.randomUUID(), "FPT", "ANNUAL_REPORT", null, Instant.now()))
        ));

        when(chunkRepository.findByIdInAndOwnerId(any(), any())).thenReturn(List.of());

        long start = System.nanoTime();
        var result = retrievalService.retrievePassages(new RetrieveRequest("Doanh thu FPT", null));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(result).isNotNull();
        assertThat(elapsedMs).isLessThan(200);
    }
}
