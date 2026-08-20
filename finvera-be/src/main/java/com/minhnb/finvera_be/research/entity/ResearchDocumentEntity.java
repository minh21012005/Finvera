package com.minhnb.finvera_be.research.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "research_document")
public class ResearchDocumentEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "symbol_id")
    private UUID symbolId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private DocumentType documentType;

    @Column(name = "year", nullable = false)
    private short year;

    @Column(name = "quarter")
    private Short quarter;

    @Column(name = "source", nullable = false, length = 200)
    private String source;

    @Column(name = "publication_date", nullable = false)
    private LocalDate publicationDate;

    @Column(name = "original_filename", length = 300)
    private String originalFilename;

    @Column(name = "original_content")
    private byte[] originalContent;

    @Column(name = "content_mime_type", length = 100)
    private String contentMimeType;

    @Column(name = "extracted_text")
    private String extractedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_status", nullable = false, length = 16)
    private IngestionStatus ingestionStatus;

    @Column(name = "ingestion_failure_reason", length = 200)
    private String ingestionFailureReason;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    protected ResearchDocumentEntity() {
    }

    public ResearchDocumentEntity(
            UUID id,
            UUID ownerId,
            UUID symbolId,
            String title,
            DocumentType documentType,
            short year,
            Short quarter,
            String source,
            LocalDate publicationDate,
            String originalFilename,
            byte[] originalContent,
            String contentMimeType,
            String extractedText,
            IngestionStatus ingestionStatus,
            String ingestionFailureReason,
            Instant submittedAt,
            Instant processedAt,
            String idempotencyKey) {
        this.id = id;
        this.ownerId = ownerId;
        this.symbolId = symbolId;
        this.title = title;
        this.documentType = documentType;
        this.year = year;
        this.quarter = quarter;
        this.source = source;
        this.publicationDate = publicationDate;
        this.originalFilename = originalFilename;
        this.originalContent = originalContent;
        this.contentMimeType = contentMimeType;
        this.extractedText = extractedText;
        this.ingestionStatus = ingestionStatus;
        this.ingestionFailureReason = ingestionFailureReason;
        this.submittedAt = submittedAt;
        this.processedAt = processedAt;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getSymbolId() {
        return symbolId;
    }

    public String getTitle() {
        return title;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public short getYear() {
        return year;
    }

    public Short getQuarter() {
        return quarter;
    }

    public String getSource() {
        return source;
    }

    public LocalDate getPublicationDate() {
        return publicationDate;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public byte[] getOriginalContent() {
        return originalContent;
    }

    public String getContentMimeType() {
        return contentMimeType;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public IngestionStatus getIngestionStatus() {
        return ingestionStatus;
    }

    public String getIngestionFailureReason() {
        return ingestionFailureReason;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public void setIngestionStatus(IngestionStatus ingestionStatus) {
        this.ingestionStatus = ingestionStatus;
    }

    public void setIngestionFailureReason(String ingestionFailureReason) {
        this.ingestionFailureReason = ingestionFailureReason;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
