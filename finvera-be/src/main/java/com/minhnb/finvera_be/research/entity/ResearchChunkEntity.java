package com.minhnb.finvera_be.research.entity;

import com.minhnb.finvera_be.research.domain.ResearchItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "research_chunk")
public class ResearchChunkEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 16)
    private ResearchItemType itemType;

    @Column(name = "research_document_id")
    private UUID researchDocumentId;

    @Column(name = "news_article_id")
    private UUID newsArticleId;

    @Column(name = "chunk_index", nullable = false)
    private short chunkIndex;

    @Column(name = "page_number")
    private Short pageNumber;

    @Column(name = "paragraph_index")
    private Short paragraphIndex;

    @Column(name = "content_text", nullable = false)
    private String contentText;

    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(Types.CHAR)
    private String contentHash;

    @Column(name = "vector_point_id", nullable = false, unique = true)
    private UUID vectorPointId;

    @Column(name = "embedding_version", nullable = false, length = 64)
    private String embeddingVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ResearchChunkEntity() {
    }

    public ResearchChunkEntity(
            UUID id,
            UUID ownerId,
            ResearchItemType itemType,
            UUID researchDocumentId,
            UUID newsArticleId,
            short chunkIndex,
            Short pageNumber,
            Short paragraphIndex,
            String contentText,
            String contentHash,
            UUID vectorPointId,
            String embeddingVersion,
            Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.itemType = itemType;
        this.researchDocumentId = researchDocumentId;
        this.newsArticleId = newsArticleId;
        this.chunkIndex = chunkIndex;
        this.pageNumber = pageNumber;
        this.paragraphIndex = paragraphIndex;
        this.contentText = contentText;
        this.contentHash = contentHash;
        this.vectorPointId = vectorPointId;
        this.embeddingVersion = embeddingVersion;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public ResearchItemType getItemType() {
        return itemType;
    }

    public UUID getResearchDocumentId() {
        return researchDocumentId;
    }

    public UUID getNewsArticleId() {
        return newsArticleId;
    }

    public short getChunkIndex() {
        return chunkIndex;
    }

    public Short getPageNumber() {
        return pageNumber;
    }

    public Short getParagraphIndex() {
        return paragraphIndex;
    }

    public String getContentText() {
        return contentText;
    }

    public String getContentHash() {
        return contentHash;
    }

    public UUID getVectorPointId() {
        return vectorPointId;
    }

    public String getEmbeddingVersion() {
        return embeddingVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
