package com.minhnb.finvera_be.research.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.research.domain.DocumentType;
import com.minhnb.finvera_be.research.domain.IngestionStatus;
import com.minhnb.finvera_be.research.domain.ResearchItemType;
import com.minhnb.finvera_be.research.dto.DocumentPageResponse;
import com.minhnb.finvera_be.research.dto.DocumentResponse;
import com.minhnb.finvera_be.research.dto.SubmitDocumentCommand;
import com.minhnb.finvera_be.research.entity.ResearchChunkEntity;
import com.minhnb.finvera_be.research.entity.ResearchDocumentEntity;
import com.minhnb.finvera_be.research.provider.ResearchAiClient;
import com.minhnb.finvera_be.research.repository.ResearchChunkRepository;
import com.minhnb.finvera_be.research.repository.ResearchDocumentRepository;
import com.minhnb.finvera_be.research.service.ResearchExceptions.DocumentNotFoundException;
import com.minhnb.finvera_be.research.service.ResearchExceptions.DuplicateSubmissionException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResearchDocumentService {

    private static final Logger log = LoggerFactory.getLogger(ResearchDocumentService.class);

    private final ResearchDocumentRepository documentRepository;
    private final ResearchChunkRepository chunkRepository;
    private final MarketReferenceDataService marketReferenceData;
    private final ResearchAiClient aiClient;
    private final OwnerScopedResearchAccess ownerAccess;

    public ResearchDocumentService(
            ResearchDocumentRepository documentRepository,
            ResearchChunkRepository chunkRepository,
            MarketReferenceDataService marketReferenceData,
            ResearchAiClient aiClient,
            OwnerScopedResearchAccess ownerAccess) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.marketReferenceData = marketReferenceData;
        this.aiClient = aiClient;
        this.ownerAccess = ownerAccess;
    }

    @Transactional
    public DocumentResponse submitDocument(SubmitDocumentCommand command) {
        UUID ownerId = ownerAccess.getAuthenticatedOwnerId();

        boolean hasFile = command.fileContent() != null && command.fileContent().length > 0;
        boolean hasText = command.pastedText() != null && !command.pastedText().isBlank();

        if (hasFile == hasText) {
            throw new IllegalArgumentException("Exactly one of file or pastedText must be provided");
        }

        // Idempotency check before creating any row or ingestion job (research R-013)
        var existing = documentRepository.findByOwnerIdAndIdempotencyKey(ownerId, command.idempotencyKey());
        if (existing.isPresent()) {
            throw new DuplicateSubmissionException(existing.get().getId());
        }

        UUID symbolId = null;
        if (command.symbol() != null && !command.symbol().isBlank()) {
            symbolId = marketReferenceData.findInstrumentBySymbolIncludingDelisted(command.symbol().trim().toUpperCase())
                    .map(InstrumentReference::instrumentId)
                    .orElse(null);
        }

        UUID docId = UUID.randomUUID();
        Short quarterVal = command.quarter() != null ? command.quarter().shortValue() : null;

        ResearchDocumentEntity doc = new ResearchDocumentEntity(
                docId,
                ownerId,
                symbolId,
                command.title().trim(),
                command.documentType(),
                (short) command.year(),
                quarterVal,
                command.source().trim(),
                command.publicationDate(),
                command.originalFilename(),
                command.fileContent(),
                hasFile ? "application/pdf" : "text/plain",
                hasText ? command.pastedText().trim() : null,
                IngestionStatus.PENDING,
                null,
                Instant.now(),
                null,
                command.idempotencyKey());

        ResearchDocumentEntity saved = documentRepository.save(doc);

        try {
            aiClient.submitIngestion(
                    saved.getId(),
                    "DOCUMENT",
                    ownerId,
                    command.fileContent(),
                    command.originalFilename(),
                    command.pastedText(),
                    hasFile ? "application/pdf" : "text/plain");
        } catch (Exception e) {
            log.error("Failed to dispatch ingestion job for document {}: {}", saved.getId(), e.getMessage());
            // Item stays PENDING, bounded timeout job will handle if never processed
        }

        return toResponse(saved, command.symbol());
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID documentId) {
        UUID ownerId = ownerAccess.getAuthenticatedOwnerId();
        ResearchDocumentEntity doc = documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        String symbol = resolveSymbol(doc.getSymbolId());
        return toResponse(doc, symbol);
    }

    @Transactional(readOnly = true)
    public DocumentPageResponse listDocuments(
            String symbol,
            DocumentType documentType,
            LocalDate dateFrom,
            LocalDate dateTo,
            int limit,
            int offset) {
        UUID ownerId = ownerAccess.getAuthenticatedOwnerId();

        UUID symbolId = null;
        if (symbol != null && !symbol.isBlank()) {
            symbolId = marketReferenceData.findInstrumentBySymbolIncludingDelisted(symbol.trim().toUpperCase())
                    .map(InstrumentReference::instrumentId)
                    .orElse(null);
        }

        int pageNumber = offset / Math.max(1, limit);
        var page = documentRepository.searchDocuments(
                ownerId,
                symbolId,
                documentType,
                dateFrom,
                dateTo,
                PageRequest.of(pageNumber, limit));

        List<DocumentResponse> items = page.getContent().stream()
                .map(d -> toResponse(d, resolveSymbol(d.getSymbolId())))
                .toList();

        return new DocumentPageResponse(items, page.getTotalElements(), limit, offset);
    }

    @Transactional
    public void deleteDocument(UUID documentId) {
        UUID ownerId = ownerAccess.getAuthenticatedOwnerId();
        ResearchDocumentEntity doc = documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        // Gather chunk vector IDs for Qdrant vector cleanup (DATA-004, FR-016)
        List<UUID> vectorIds = chunkRepository.findByResearchDocumentIdAndOwnerId(documentId, ownerId).stream()
                .map(ResearchChunkEntity::getVectorPointId)
                .toList();

        if (!vectorIds.isEmpty()) {
            try {
                aiClient.deleteVectors(vectorIds);
            } catch (Exception e) {
                log.warn("Failed to delete vectors for document {}: {}", documentId, e.getMessage());
            }
        }

        documentRepository.delete(doc);
    }

    private String resolveSymbol(UUID symbolId) {
        if (symbolId == null) {
            return null;
        }
        return marketReferenceData.findInstrumentsByIds(List.of(symbolId)).stream()
                .findFirst()
                .map(InstrumentReference::symbol)
                .orElse(null);
    }

    private DocumentResponse toResponse(ResearchDocumentEntity doc, String symbol) {
        Integer quarterVal = doc.getQuarter() != null ? doc.getQuarter().intValue() : null;
        return new DocumentResponse(
                doc.getId(),
                doc.getTitle(),
                symbol,
                doc.getDocumentType(),
                doc.getYear(),
                quarterVal,
                doc.getSource(),
                doc.getPublicationDate(),
                doc.getIngestionStatus(),
                doc.getIngestionFailureReason(),
                doc.getSubmittedAt(),
                doc.getProcessedAt());
    }
}
