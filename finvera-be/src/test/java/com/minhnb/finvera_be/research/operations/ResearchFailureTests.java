package com.minhnb.finvera_be.research.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.research.domain.Applicability;
import com.minhnb.finvera_be.research.domain.DocumentType;
import com.minhnb.finvera_be.research.domain.IngestionStatus;
import com.minhnb.finvera_be.research.dto.RetrieveRequest;
import com.minhnb.finvera_be.research.dto.SubmitDocumentCommand;
import com.minhnb.finvera_be.research.entity.NewsArticleEntity;
import com.minhnb.finvera_be.research.entity.ResearchDocumentEntity;
import com.minhnb.finvera_be.research.provider.ResearchAiClient;
import com.minhnb.finvera_be.research.repository.NewsArticleRepository;
import com.minhnb.finvera_be.research.repository.ResearchChunkRepository;
import com.minhnb.finvera_be.research.repository.ResearchDocumentRepository;
import com.minhnb.finvera_be.research.service.IngestionCallbackService;
import com.minhnb.finvera_be.research.service.OwnerScopedResearchAccess;
import com.minhnb.finvera_be.research.service.ResearchDocumentService;
import com.minhnb.finvera_be.research.service.ResearchExceptions.RetrievalUnavailableException;
import com.minhnb.finvera_be.research.service.RetrievalService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResearchFailureTests {

    private ResearchAiClient aiClient;
    private ResearchDocumentRepository documentRepository;
    private NewsArticleRepository newsRepository;
    private ResearchChunkRepository chunkRepository;
    private MarketReferenceDataService marketReferenceData;
    private OwnerScopedResearchAccess ownerAccess;
    private ResearchProperties properties;

    private ResearchDocumentService documentService;
    private RetrievalService retrievalService;
    private IngestionCallbackService callbackService;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        aiClient = mock(ResearchAiClient.class);
        documentRepository = mock(ResearchDocumentRepository.class);
        newsRepository = mock(NewsArticleRepository.class);
        chunkRepository = mock(ResearchChunkRepository.class);
        marketReferenceData = mock(MarketReferenceDataService.class);
        ownerAccess = mock(OwnerScopedResearchAccess.class);
        properties = new ResearchProperties(
                "test-api-key",
                "http://localhost:8001",
                Duration.ofMinutes(15),
                Duration.ofMinutes(1),
                20 * 1024 * 1024L);

        when(ownerAccess.getAuthenticatedOwnerId()).thenReturn(ownerId);

        documentService = new ResearchDocumentService(
                documentRepository,
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

        callbackService = new IngestionCallbackService(
                documentRepository,
                newsRepository,
                chunkRepository,
                properties);
    }

    @Test
    void ai_service_down_during_submission_does_not_crash_and_stays_pending() {
        SubmitDocumentCommand command = new SubmitDocumentCommand(
                "Báo cáo tài chính",
                "FPT",
                DocumentType.ANNUAL_REPORT,
                2025,
                null,
                "FPT",
                LocalDate.now(),
                null,
                null,
                "Nội dung báo cáo...",
                "fault-idem-1");

        when(documentRepository.findByOwnerIdAndIdempotencyKey(ownerId, "fault-idem-1")).thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Simulate AI service failure on dispatch
        doThrow(new RuntimeException("Connection refused")).when(aiClient).submitIngestion(any(), any(), any(), any(), any(), any(), any());

        var response = documentService.submitDocument(command);

        assertThat(response).isNotNull();
        assertThat(response.ingestionStatus()).isEqualTo(IngestionStatus.PENDING);
    }

    @Test
    void ai_service_down_during_retrieval_throws_retrieval_unavailable_exception() {
        when(aiClient.retrieveChunks(any())).thenThrow(new RuntimeException("AI service unavailable"));

        assertThatThrownBy(() -> retrievalService.retrievePassages(new RetrieveRequest("Doanh thu FPT", null)))
                .isInstanceOf(RetrievalUnavailableException.class)
                .hasMessageContaining("Retrieval service is currently unavailable");
    }

    @Test
    void timeout_reaper_marks_stuck_pending_and_processing_items_as_failed() {
        Instant pastCutoff = Instant.now().minus(Duration.ofMinutes(30));

        ResearchDocumentEntity stuckDoc = new ResearchDocumentEntity(
                UUID.randomUUID(),
                ownerId,
                null,
                "Báo cáo kẹt",
                DocumentType.OTHER,
                (short) 2025,
                null,
                "Nguồn",
                LocalDate.now(),
                null,
                null,
                null,
                null,
                IngestionStatus.PENDING,
                null,
                pastCutoff,
                null,
                "stuck-idem-doc");

        NewsArticleEntity stuckNews = new NewsArticleEntity(
                UUID.randomUUID(),
                ownerId,
                "Tin tức kẹt",
                "Nguồn",
                pastCutoff,
                "Nội dung",
                null,
                null,
                Applicability.MISSING,
                null,
                Applicability.MISSING,
                null,
                Applicability.MISSING,
                null,
                IngestionStatus.PROCESSING,
                null,
                pastCutoff,
                null,
                "stuck-idem-news",
                new HashSet<>());

        when(documentRepository.findAll()).thenReturn(List.of(stuckDoc));
        when(newsRepository.findAll()).thenReturn(List.of(stuckNews));

        int failedCount = callbackService.checkAndFailStuckProcessingItems();

        assertThat(failedCount).isEqualTo(2);
        assertThat(stuckDoc.getIngestionStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(stuckDoc.getIngestionFailureReason()).isEqualTo("PROCESSING_TIMEOUT");
        assertThat(stuckNews.getIngestionStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(stuckNews.getIngestionFailureReason()).isEqualTo("PROCESSING_TIMEOUT");

        verify(documentRepository).save(stuckDoc);
        verify(newsRepository).save(stuckNews);
    }
}
