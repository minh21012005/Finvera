package com.minhnb.finvera_be.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.research.dto.IngestionCallbackRequest;
import com.minhnb.finvera_be.research.dto.IngestionCallbackRequest.IngestedChunkDto;
import com.minhnb.finvera_be.research.entity.DocumentType;
import com.minhnb.finvera_be.research.entity.IngestionStatus;
import com.minhnb.finvera_be.research.entity.ResearchDocumentEntity;
import com.minhnb.finvera_be.research.repository.NewsArticleRepository;
import com.minhnb.finvera_be.research.repository.ResearchChunkRepository;
import com.minhnb.finvera_be.research.repository.ResearchDocumentRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IngestionCallbackServiceTests {

    private ResearchDocumentRepository documentRepository;
    private NewsArticleRepository newsRepository;
    private ResearchChunkRepository chunkRepository;
    private ResearchProperties properties;
    private IngestionCallbackService service;

    @BeforeEach
    void setUp() {
        documentRepository = mock(ResearchDocumentRepository.class);
        newsRepository = mock(NewsArticleRepository.class);
        chunkRepository = mock(ResearchChunkRepository.class);
        properties = new ResearchProperties("test-key", "http://localhost:8000/internal/v1", Duration.ofMinutes(10), 20971520L);
        service = new IngestionCallbackService(documentRepository, newsRepository, chunkRepository, properties);
    }

    @Test
    void updatesDocumentToReadyAndPersistsChunksOnSuccessfulCallback() {
        UUID docId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        ResearchDocumentEntity doc = new ResearchDocumentEntity(
                docId, ownerId, null, "Test Title", DocumentType.ANNUAL_REPORT, (short) 2026, null, "Source",
                LocalDate.now(), null, null, null, null, IngestionStatus.PROCESSING, null,
                Instant.now(), null, "idem-1");

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        IngestedChunkDto chunk1 = new IngestedChunkDto(
                UUID.randomUUID(), (short) 0, (short) 1, null, "Chunk 1 text", "hash1", UUID.randomUUID(), "gemini-embedding-v1");

        IngestionCallbackRequest request = new IngestionCallbackRequest(
                "READY", null, "Full extracted text", List.of(chunk1), null);

        service.handleCallback(docId, request);

        assertThat(doc.getIngestionStatus()).isEqualTo(IngestionStatus.READY);
        assertThat(doc.getExtractedText()).isEqualTo("Full extracted text");
        assertThat(doc.getProcessedAt()).isNotNull();
        assertThat(doc.getIngestionFailureReason()).isNull();

        verify(chunkRepository).saveAll(any());
        verify(documentRepository).save(doc);
    }

    @Test
    void updatesDocumentToFailedOnFailedCallback() {
        UUID docId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        ResearchDocumentEntity doc = new ResearchDocumentEntity(
                docId, ownerId, null, "Test Title", DocumentType.ANNUAL_REPORT, (short) 2026, null, "Source",
                LocalDate.now(), null, null, null, null, IngestionStatus.PROCESSING, null,
                Instant.now(), null, "idem-2");

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        IngestionCallbackRequest request = new IngestionCallbackRequest(
                "FAILED", "PDF corrupted or unparseable", null, List.of(), null);

        service.handleCallback(docId, request);

        assertThat(doc.getIngestionStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(doc.getIngestionFailureReason()).isEqualTo("PDF corrupted or unparseable");
        assertThat(doc.getProcessedAt()).isNotNull();

        verify(documentRepository).save(doc);
    }

    @Test
    void timesOutStuckProcessingItems() {
        UUID docId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant stuckTime = Instant.now().minus(Duration.ofMinutes(15));
        ResearchDocumentEntity doc = new ResearchDocumentEntity(
                docId, ownerId, null, "Stuck Doc", DocumentType.ANNUAL_REPORT, (short) 2026, null, "Source",
                LocalDate.now(), null, null, null, null, IngestionStatus.PROCESSING, null,
                stuckTime, null, "idem-3");

        when(documentRepository.findAll()).thenReturn(List.of(doc));
        when(newsRepository.findAll()).thenReturn(List.of());

        int timedOutCount = service.checkAndFailStuckProcessingItems();

        assertThat(timedOutCount).isEqualTo(1);
        assertThat(doc.getIngestionStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(doc.getIngestionFailureReason()).isEqualTo("PROCESSING_TIMEOUT");

        verify(documentRepository).save(doc);
    }
}
