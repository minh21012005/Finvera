package com.minhnb.finvera_be.research.service;

import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.research.domain.Applicability;
import com.minhnb.finvera_be.research.domain.ImpactLevel;
import com.minhnb.finvera_be.research.domain.IngestionStatus;
import com.minhnb.finvera_be.research.domain.NewsCategory;
import com.minhnb.finvera_be.research.domain.ResearchItemType;
import com.minhnb.finvera_be.research.domain.Sentiment;
import com.minhnb.finvera_be.research.dto.IngestionCallbackRequest;
import com.minhnb.finvera_be.research.entity.NewsArticleEntity;
import com.minhnb.finvera_be.research.entity.ResearchChunkEntity;
import com.minhnb.finvera_be.research.entity.ResearchDocumentEntity;
import com.minhnb.finvera_be.research.repository.NewsArticleRepository;
import com.minhnb.finvera_be.research.repository.ResearchChunkRepository;
import com.minhnb.finvera_be.research.repository.ResearchDocumentRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionCallbackService {

    private static final Logger log = LoggerFactory.getLogger(IngestionCallbackService.class);

    private final ResearchDocumentRepository documentRepository;
    private final NewsArticleRepository newsRepository;
    private final ResearchChunkRepository chunkRepository;
    private final ResearchProperties properties;

    public IngestionCallbackService(
            ResearchDocumentRepository documentRepository,
            NewsArticleRepository newsRepository,
            ResearchChunkRepository chunkRepository,
            ResearchProperties properties) {
        this.documentRepository = documentRepository;
        this.newsRepository = newsRepository;
        this.chunkRepository = chunkRepository;
        this.properties = properties;
    }

    @Transactional
    public void handleCallback(UUID researchItemId, IngestionCallbackRequest request) {
        var docOpt = documentRepository.findById(researchItemId);
        if (docOpt.isPresent()) {
            handleDocumentCallback(docOpt.get(), request);
            return;
        }

        var newsOpt = newsRepository.findById(researchItemId);
        if (newsOpt.isPresent()) {
            handleNewsCallback(newsOpt.get(), request);
            return;
        }

        throw new NoSuchElementException("Unknown research item id: " + researchItemId);
    }

    private void handleDocumentCallback(ResearchDocumentEntity doc, IngestionCallbackRequest request) {
        if ("READY".equalsIgnoreCase(request.status())) {
            doc.setExtractedText(request.extractedText());
            doc.setIngestionStatus(IngestionStatus.READY);
            doc.setIngestionFailureReason(null);
            doc.setProcessedAt(Instant.now());

            if (request.chunks() != null && !request.chunks().isEmpty()) {
                List<ResearchChunkEntity> entities = new ArrayList<>();
                for (var c : request.chunks()) {
                    UUID chunkId = c.id() != null ? c.id() : UUID.randomUUID();
                    UUID vectorPointId = c.vectorPointId() != null ? c.vectorPointId() : UUID.randomUUID();
                    entities.add(new ResearchChunkEntity(
                            chunkId,
                            doc.getOwnerId(),
                            ResearchItemType.DOCUMENT,
                            doc.getId(),
                            null,
                            c.chunkIndex(),
                            c.pageNumber() != null ? c.pageNumber() : (short) 1,
                            null,
                            c.contentText(),
                            c.contentHash(),
                            vectorPointId,
                            c.embeddingVersion() != null ? c.embeddingVersion() : "gemini-embedding-v1",
                            Instant.now()));
                }
                chunkRepository.saveAll(entities);
            }
        } else {
            doc.setIngestionStatus(IngestionStatus.FAILED);
            String reason = request.failureReason();
            doc.setIngestionFailureReason(reason != null && !reason.isBlank() ? reason : "Ingestion processing failed");
            doc.setProcessedAt(Instant.now());
        }
        documentRepository.save(doc);
    }

    private void handleNewsCallback(NewsArticleEntity article, IngestionCallbackRequest request) {
        if ("READY".equalsIgnoreCase(request.status())) {
            article.setIngestionStatus(IngestionStatus.READY);
            article.setIngestionFailureReason(null);
            article.setProcessedAt(Instant.now());

            if (request.classification() != null) {
                var cls = request.classification();
                if (cls.category() != null) {
                    try {
                        article.setCategory(NewsCategory.valueOf(cls.category().toUpperCase()));
                        article.setCategoryApplicability(Applicability.DEFINED);
                    } catch (Exception ignored) {
                    }
                }
                if (cls.sentiment() != null) {
                    try {
                        article.setSentiment(Sentiment.valueOf(cls.sentiment().toUpperCase()));
                        article.setSentimentApplicability(Applicability.DEFINED);
                    } catch (Exception ignored) {
                    }
                }
                if (cls.impactLevel() != null) {
                    try {
                        article.setImpactLevel(ImpactLevel.valueOf(cls.impactLevel().toUpperCase()));
                        article.setImpactApplicability(Applicability.DEFINED);
                    } catch (Exception ignored) {
                    }
                }
                if (cls.sector() != null) {
                    article.setSector(cls.sector());
                }
            }

            if (request.chunks() != null && !request.chunks().isEmpty()) {
                List<ResearchChunkEntity> entities = new ArrayList<>();
                for (var c : request.chunks()) {
                    UUID chunkId = c.id() != null ? c.id() : UUID.randomUUID();
                    UUID vectorPointId = c.vectorPointId() != null ? c.vectorPointId() : UUID.randomUUID();
                    entities.add(new ResearchChunkEntity(
                            chunkId,
                            article.getOwnerId(),
                            ResearchItemType.NEWS_ARTICLE,
                            null,
                            article.getId(),
                            c.chunkIndex(),
                            null,
                            c.paragraphIndex() != null ? c.paragraphIndex() : (short) 0,
                            c.contentText(),
                            c.contentHash(),
                            vectorPointId,
                            c.embeddingVersion() != null ? c.embeddingVersion() : "gemini-embedding-v1",
                            Instant.now()));
                }
                chunkRepository.saveAll(entities);
            }
        } else {
            article.setIngestionStatus(IngestionStatus.FAILED);
            String reason = request.failureReason();
            article.setIngestionFailureReason(reason != null && !reason.isBlank() ? reason : "Ingestion processing failed");
            article.setProcessedAt(Instant.now());
        }
        newsRepository.save(article);
    }

    @Transactional
    public int checkAndFailStuckProcessingItems() {
        Instant cutoff = Instant.now().minus(properties.ingestionTimeout());
        int count = 0;

        for (var doc : documentRepository.findAll()) {
            if ((doc.getIngestionStatus() == IngestionStatus.PENDING || doc.getIngestionStatus() == IngestionStatus.PROCESSING)
                    && doc.getSubmittedAt().isBefore(cutoff)) {
                doc.setIngestionStatus(IngestionStatus.FAILED);
                doc.setIngestionFailureReason("PROCESSING_TIMEOUT");
                doc.setProcessedAt(Instant.now());
                documentRepository.save(doc);
                count++;
            }
        }

        for (var article : newsRepository.findAll()) {
            if ((article.getIngestionStatus() == IngestionStatus.PENDING || article.getIngestionStatus() == IngestionStatus.PROCESSING)
                    && article.getSubmittedAt().isBefore(cutoff)) {
                article.setIngestionStatus(IngestionStatus.FAILED);
                article.setIngestionFailureReason("PROCESSING_TIMEOUT");
                article.setProcessedAt(Instant.now());
                newsRepository.save(article);
                count++;
            }
        }

        return count;
    }
}
