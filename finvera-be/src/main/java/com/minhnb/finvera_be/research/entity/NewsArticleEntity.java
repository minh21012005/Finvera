package com.minhnb.finvera_be.research.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "news_article")
public class NewsArticleEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "source", nullable = false, length = 200)
    private String source;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "reference_url", length = 2000)
    private String referenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 16)
    private NewsCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_applicability", nullable = false, length = 32)
    private Applicability categoryApplicability = Applicability.MISSING;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentiment", length = 16)
    private Sentiment sentiment;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentiment_applicability", nullable = false, length = 32)
    private Applicability sentimentApplicability = Applicability.MISSING;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", length = 16)
    private ImpactLevel impactLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_applicability", nullable = false, length = 32)
    private Applicability impactApplicability = Applicability.MISSING;

    @Column(name = "sector", length = 100)
    private String sector;

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

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "news_article_symbol", joinColumns = @JoinColumn(name = "news_article_id"))
    @Column(name = "instrument_id")
    private Set<UUID> mentionedInstrumentIds = new HashSet<>();

    protected NewsArticleEntity() {
    }

    public NewsArticleEntity(
            UUID id,
            UUID ownerId,
            String title,
            String source,
            Instant publishedAt,
            String body,
            String referenceUrl,
            NewsCategory category,
            Applicability categoryApplicability,
            Sentiment sentiment,
            Applicability sentimentApplicability,
            ImpactLevel impactLevel,
            Applicability impactApplicability,
            String sector,
            IngestionStatus ingestionStatus,
            String ingestionFailureReason,
            Instant submittedAt,
            Instant processedAt,
            String idempotencyKey,
            Set<UUID> mentionedInstrumentIds) {
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.source = source;
        this.publishedAt = publishedAt;
        this.body = body;
        this.referenceUrl = referenceUrl;
        this.category = category;
        this.categoryApplicability = categoryApplicability != null ? categoryApplicability : Applicability.MISSING;
        this.sentiment = sentiment;
        this.sentimentApplicability = sentimentApplicability != null ? sentimentApplicability : Applicability.MISSING;
        this.impactLevel = impactLevel;
        this.impactApplicability = impactApplicability != null ? impactApplicability : Applicability.MISSING;
        this.sector = sector;
        this.ingestionStatus = ingestionStatus;
        this.ingestionFailureReason = ingestionFailureReason;
        this.submittedAt = submittedAt;
        this.processedAt = processedAt;
        this.idempotencyKey = idempotencyKey;
        if (mentionedInstrumentIds != null) {
            this.mentionedInstrumentIds.addAll(mentionedInstrumentIds);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public String getSource() {
        return source;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getBody() {
        return body;
    }

    public String getReferenceUrl() {
        return referenceUrl;
    }

    public NewsCategory getCategory() {
        return category;
    }

    public Applicability getCategoryApplicability() {
        return categoryApplicability;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public Applicability getSentimentApplicability() {
        return sentimentApplicability;
    }

    public ImpactLevel getImpactLevel() {
        return impactLevel;
    }

    public Applicability getImpactApplicability() {
        return impactApplicability;
    }

    public String getSector() {
        return sector;
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

    public Set<UUID> getMentionedInstrumentIds() {
        return mentionedInstrumentIds;
    }

    public void setCategory(NewsCategory category) {
        this.category = category;
    }

    public void setCategoryApplicability(Applicability categoryApplicability) {
        this.categoryApplicability = categoryApplicability;
    }

    public void setSentiment(Sentiment sentiment) {
        this.sentiment = sentiment;
    }

    public void setSentimentApplicability(Applicability sentimentApplicability) {
        this.sentimentApplicability = sentimentApplicability;
    }

    public void setImpactLevel(ImpactLevel impactLevel) {
        this.impactLevel = impactLevel;
    }

    public void setImpactApplicability(Applicability impactApplicability) {
        this.impactApplicability = impactApplicability;
    }

    public void setSector(String sector) {
        this.sector = sector;
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

    public void setMentionedInstrumentIds(Set<UUID> mentionedInstrumentIds) {
        this.mentionedInstrumentIds = mentionedInstrumentIds != null ? mentionedInstrumentIds : new HashSet<>();
    }
}
