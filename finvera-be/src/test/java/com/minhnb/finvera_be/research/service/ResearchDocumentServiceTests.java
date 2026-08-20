package com.minhnb.finvera_be.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.research.domain.DocumentType;
import com.minhnb.finvera_be.research.domain.IngestionStatus;
import com.minhnb.finvera_be.research.domain.ResearchItemType;
import com.minhnb.finvera_be.research.dto.DocumentResponse;
import com.minhnb.finvera_be.research.dto.SubmitDocumentCommand;
import com.minhnb.finvera_be.research.entity.ResearchChunkEntity;
import com.minhnb.finvera_be.research.entity.ResearchDocumentEntity;
import com.minhnb.finvera_be.research.provider.AiInternalDto.IngestionAcceptedResponse;
import com.minhnb.finvera_be.research.provider.ResearchAiClient;
import com.minhnb.finvera_be.research.repository.ResearchChunkRepository;
import com.minhnb.finvera_be.research.repository.ResearchDocumentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResearchDocumentServiceTests {

    private ResearchDocumentRepository documentRepository;
    private ResearchChunkRepository chunkRepository;
    private MarketReferenceDataService marketReferenceData;
    private ResearchAiClient aiClient;
    private OwnerScopedResearchAccess ownerAccess;
    private ResearchDocumentService service;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        documentRepository = mock(ResearchDocumentRepository.class);
        chunkRepository = mock(ResearchChunkRepository.class);
        marketReferenceData = mock(MarketReferenceDataService.class);
        aiClient = mock(ResearchAiClient.class);
        ownerAccess = mock(OwnerScopedResearchAccess.class);

        when(ownerAccess.getAuthenticatedOwnerId()).thenReturn(ownerId);

        service = new ResearchDocumentService(
                documentRepository,
                chunkRepository,
                marketReferenceData,
                aiClient,
                ownerAccess);
    }

    @Test
    void submitDocument_file_createsPendingDocumentAndCallsAiClient() {
        SubmitDocumentCommand command = new SubmitDocumentCommand(
                "FPT Annual Report 2025",
                "FPT",
                DocumentType.ANNUAL_REPORT,
                2025,
                null,
                "FPT Investor Relations",
                LocalDate.of(2026, 3, 15),
                "fake pdf content".getBytes(),
                "fpt_ar_2025.pdf",
                null,
                "idem-doc-1");

        UUID instrumentId = UUID.randomUUID();
        InstrumentReference instrument = new InstrumentReference(
                instrumentId, "HOSE", "FPT", "STOCK", "ACTIVE");
        when(marketReferenceData.findInstrumentBySymbolIncludingDelisted("FPT")).thenReturn(Optional.of(instrument));

        when(documentRepository.findByOwnerIdAndIdempotencyKey(ownerId, "idem-doc-1")).thenReturn(Optional.empty());
        when(documentRepository.save(any(ResearchDocumentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        when(aiClient.submitIngestion(any(), eq("DOCUMENT"), eq(ownerId), any(), eq("fpt_ar_2025.pdf"), any(), any()))
                .thenReturn(new IngestionAcceptedResponse(UUID.randomUUID(), "PROCESSING", Instant.now()));

        DocumentResponse response = service.submitDocument(command);

        assertThat(response.title()).isEqualTo("FPT Annual Report 2025");
        assertThat(response.symbol()).isEqualTo("FPT");
        assertThat(response.ingestionStatus()).isEqualTo(IngestionStatus.PENDING);

        ArgumentCaptor<ResearchDocumentEntity> captor = ArgumentCaptor.forClass(ResearchDocumentEntity.class);
        verify(documentRepository).save(captor.capture());
        ResearchDocumentEntity saved = captor.getValue();
        assertThat(saved.getOriginalFilename()).isEqualTo("fpt_ar_2025.pdf");
        assertThat(saved.getOriginalContent()).isNotNull();

        verify(aiClient).submitIngestion(eq(saved.getId()), eq("DOCUMENT"), eq(ownerId), any(), eq("fpt_ar_2025.pdf"), eq(null), any());
    }

    @Test
    void submitDocument_duplicateIdempotencyKey_throwsDuplicateSubmissionException() {
        SubmitDocumentCommand command = new SubmitDocumentCommand(
                "FPT Annual Report 2025",
                "FPT",
                DocumentType.ANNUAL_REPORT,
                2025,
                null,
                "FPT Investor Relations",
                LocalDate.of(2026, 3, 15),
                "fake pdf content".getBytes(),
                "fpt_ar_2025.pdf",
                null,
                "idem-duplicate");

        UUID existingId = UUID.randomUUID();
        ResearchDocumentEntity existing = new ResearchDocumentEntity(
                existingId, ownerId, null, "Existing Doc", DocumentType.ANNUAL_REPORT, (short) 2025, null, "Source",
                LocalDate.of(2026, 3, 15), "file.pdf", "data".getBytes(), "application/pdf", null, IngestionStatus.PENDING,
                null, Instant.now(), null, "idem-duplicate");
        when(documentRepository.findByOwnerIdAndIdempotencyKey(ownerId, "idem-duplicate")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.submitDocument(command))
                .isInstanceOf(ResearchExceptions.DuplicateSubmissionException.class)
                .hasMessageContaining(existingId.toString());
    }

    @Test
    void deleteDocument_deletesDocumentAndCleansUpVectors() {
        UUID docId = UUID.randomUUID();
        ResearchDocumentEntity doc = new ResearchDocumentEntity(
                docId, ownerId, null, "Test Doc", DocumentType.ANNUAL_REPORT, (short) 2025, null, "Source",
                LocalDate.of(2026, 3, 15), "file.pdf", "data".getBytes(), "application/pdf", null, IngestionStatus.READY,
                null, Instant.now(), Instant.now(), "idem-del");

        when(documentRepository.findByIdAndOwnerId(docId, ownerId)).thenReturn(Optional.of(doc));

        UUID vectorId1 = UUID.randomUUID();
        UUID vectorId2 = UUID.randomUUID();
        ResearchChunkEntity c1 = new ResearchChunkEntity(
                UUID.randomUUID(), ownerId, ResearchItemType.DOCUMENT, docId, null, (short) 0, (short) 1, null,
                "Chunk 1", "hash1", vectorId1, "gemini-embedding-v1", Instant.now());
        ResearchChunkEntity c2 = new ResearchChunkEntity(
                UUID.randomUUID(), ownerId, ResearchItemType.DOCUMENT, docId, null, (short) 1, (short) 2, null,
                "Chunk 2", "hash2", vectorId2, "gemini-embedding-v1", Instant.now());

        when(chunkRepository.findByResearchDocumentIdAndOwnerId(docId, ownerId)).thenReturn(List.of(c1, c2));

        service.deleteDocument(docId);

        verify(aiClient).deleteVectors(List.of(vectorId1, vectorId2));
        verify(documentRepository).delete(doc);
    }

    @Test
    void deleteDocument_vectorCleanupFails_doesNotDeletePostgresRowAndPropagates() {
        UUID docId = UUID.randomUUID();
        ResearchDocumentEntity doc = new ResearchDocumentEntity(
                docId, ownerId, null, "Test Doc", DocumentType.ANNUAL_REPORT, (short) 2025, null, "Source",
                LocalDate.of(2026, 3, 15), "file.pdf", "data".getBytes(), "application/pdf", null, IngestionStatus.READY,
                null, Instant.now(), Instant.now(), "idem-del-fail");

        when(documentRepository.findByIdAndOwnerId(docId, ownerId)).thenReturn(Optional.of(doc));

        UUID vectorId1 = UUID.randomUUID();
        ResearchChunkEntity c1 = new ResearchChunkEntity(
                UUID.randomUUID(), ownerId, ResearchItemType.DOCUMENT, docId, null, (short) 0, (short) 1, null,
                "Chunk 1", "hash1", vectorId1, "gemini-embedding-v1", Instant.now());

        when(chunkRepository.findByResearchDocumentIdAndOwnerId(docId, ownerId)).thenReturn(List.of(c1));
        doThrow(new RuntimeException("Qdrant unreachable")).when(aiClient).deleteVectors(List.of(vectorId1));

        assertThatThrownBy(() -> service.deleteDocument(docId))
                .isInstanceOf(ResearchExceptions.VectorCleanupFailedException.class);

        verify(documentRepository, never()).delete(any());
        // The still-present chunk row (with its vectorPointId) is what a retried cleanup needs.
        verify(chunkRepository, never()).delete(any());
    }
}
