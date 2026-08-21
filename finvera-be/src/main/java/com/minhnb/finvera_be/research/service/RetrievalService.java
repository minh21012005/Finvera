package com.minhnb.finvera_be.research.service;

import com.minhnb.finvera_be.research.domain.ResearchItemType;
import com.minhnb.finvera_be.research.dto.PassageResponse;
import com.minhnb.finvera_be.research.dto.RetrieveRequest;
import com.minhnb.finvera_be.research.dto.RetrieveResponse;
import com.minhnb.finvera_be.research.dto.SourceType;
import com.minhnb.finvera_be.research.entity.NewsArticleEntity;
import com.minhnb.finvera_be.research.entity.ResearchChunkEntity;
import com.minhnb.finvera_be.research.entity.ResearchDocumentEntity;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RankedChunkDto;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RetrieveChunksRequest;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RetrieveChunksResponse;
import com.minhnb.finvera_be.research.provider.ResearchAiClient;
import com.minhnb.finvera_be.research.repository.NewsArticleRepository;
import com.minhnb.finvera_be.research.repository.ResearchChunkRepository;
import com.minhnb.finvera_be.research.repository.ResearchDocumentRepository;
import com.minhnb.finvera_be.research.service.ResearchExceptions.RetrievalUnavailableException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ResearchAiClient aiClient;
    private final ResearchChunkRepository chunkRepository;
    private final ResearchDocumentRepository documentRepository;
    private final NewsArticleRepository newsRepository;
    private final OwnerScopedResearchAccess ownerAccess;

    public RetrievalService(
            ResearchAiClient aiClient,
            ResearchChunkRepository chunkRepository,
            ResearchDocumentRepository documentRepository,
            NewsArticleRepository newsRepository,
            OwnerScopedResearchAccess ownerAccess) {
        this.aiClient = aiClient;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.newsRepository = newsRepository;
        this.ownerAccess = ownerAccess;
    }

    @Transactional(readOnly = true)
    public RetrieveResponse retrievePassages(RetrieveRequest request) {
        if (request.query() == null || request.query().trim().isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }

        UUID ownerId = ownerAccess.getAuthenticatedOwnerId();

        String symbol = null;
        String documentType = null;
        String newsCategory = null;
        String dateFrom = null;
        String dateTo = null;

        if (request.filters() != null) {
            var f = request.filters();
            if (f.symbol() != null && !f.symbol().isBlank()) {
                symbol = f.symbol().trim().toUpperCase();
            }
            if (f.documentType() != null) {
                documentType = f.documentType().name();
            }
            if (f.newsCategory() != null) {
                newsCategory = f.newsCategory().name();
            }
            if (f.dateFrom() != null) {
                dateFrom = f.dateFrom().toString();
            }
            if (f.dateTo() != null) {
                dateTo = f.dateTo().toString();
            }
        }

        RetrieveChunksResponse aiResponse;
        try {
            aiResponse = aiClient.retrieveChunks(new RetrieveChunksRequest(
                    request.query().trim(),
                    ownerId,
                    symbol,
                    documentType,
                    newsCategory,
                    dateFrom,
                    dateTo,
                    8));
        } catch (Exception e) {
            log.error("AI service retrieval failed: {}", e.getMessage());
            throw new RetrievalUnavailableException("Retrieval service is currently unavailable", e);
        }

        if (aiResponse == null || aiResponse.chunks() == null || aiResponse.chunks().isEmpty()) {
            return new RetrieveResponse(Collections.emptyList(), Instant.now());
        }

        List<RankedChunkDto> rankedChunks = aiResponse.chunks();
        List<UUID> chunkIds = rankedChunks.stream().map(RankedChunkDto::chunkId).toList();

        // Authoritative resolution from Postgres (F4 & rag-v1)
        List<ResearchChunkEntity> chunkEntities = chunkRepository.findByIdInAndOwnerId(chunkIds, ownerId);
        Map<UUID, ResearchChunkEntity> chunkMap = new HashMap<>();
        for (var c : chunkEntities) {
            chunkMap.put(c.getId(), c);
        }

        List<PassageResponse> passages = new ArrayList<>();

        for (var ranked : rankedChunks) {
            ResearchChunkEntity chunk = chunkMap.get(ranked.chunkId());
            if (chunk == null) {
                continue;
            }

            if (chunk.getItemType() == ResearchItemType.DOCUMENT && chunk.getResearchDocumentId() != null) {
                var docOpt = documentRepository.findByIdAndOwnerId(chunk.getResearchDocumentId(), ownerId);
                if (docOpt.isPresent()) {
                    ResearchDocumentEntity doc = docOpt.get();
                    String location = "Page " + chunk.getPageNumber();
                    passages.add(new PassageResponse(
                            chunk.getId(),
                            SourceType.DOCUMENT,
                            doc.getId(),
                            doc.getTitle(),
                            location,
                            doc.getSource(),
                            doc.getPublicationDate(),
                            chunk.getContentText(),
                            ranked.score()));
                }
            } else if (chunk.getItemType() == ResearchItemType.NEWS_ARTICLE && chunk.getNewsArticleId() != null) {
                var newsOpt = newsRepository.findByIdAndOwnerId(chunk.getNewsArticleId(), ownerId);
                if (newsOpt.isPresent()) {
                    NewsArticleEntity article = newsOpt.get();
                    int pIndex = chunk.getParagraphIndex() != null ? chunk.getParagraphIndex() + 1 : 1;
                    String location = "Paragraph " + pIndex;
                    LocalDate pubDate = article.getPublishedAt().atZone(MARKET_ZONE).toLocalDate();
                    passages.add(new PassageResponse(
                            chunk.getId(),
                            SourceType.NEWS_ARTICLE,
                            article.getId(),
                            article.getTitle(),
                            location,
                            article.getSource(),
                            pubDate,
                            chunk.getContentText(),
                            ranked.score()));
                }
            }
        }

        return new RetrieveResponse(passages, Instant.now());
    }

    /**
     * Resolves a single {@code research_chunk.id} to its full citation shape
     * (owner-scoped, authoritative from Postgres — F4/rag-v1) — the single shared
     * resolution path both {@link AskService} (Feature 006's own /research/ask) and
     * the AI Analyst (Feature 007, a different owning module) use, so a citation
     * resolves identically regardless of which feature's orchestrator produced it.
     */
    @Transactional(readOnly = true)
    public Optional<PassageResponse> resolveChunkCitation(UUID chunkId, UUID ownerId) {
        ResearchChunkEntity chunk = chunkRepository.findByIdAndOwnerId(chunkId, ownerId).orElse(null);
        if (chunk == null) {
            return Optional.empty();
        }

        if (chunk.getItemType() == ResearchItemType.DOCUMENT && chunk.getResearchDocumentId() != null) {
            return documentRepository.findByIdAndOwnerId(chunk.getResearchDocumentId(), ownerId).map(doc -> {
                String location = "Page " + chunk.getPageNumber();
                return new PassageResponse(
                        chunk.getId(),
                        SourceType.DOCUMENT,
                        doc.getId(),
                        doc.getTitle(),
                        location,
                        doc.getSource(),
                        doc.getPublicationDate(),
                        chunk.getContentText(),
                        0.0);
            });
        } else if (chunk.getItemType() == ResearchItemType.NEWS_ARTICLE && chunk.getNewsArticleId() != null) {
            return newsRepository.findByIdAndOwnerId(chunk.getNewsArticleId(), ownerId).map(article -> {
                int pIndex = chunk.getParagraphIndex() != null ? chunk.getParagraphIndex() + 1 : 1;
                String location = "Paragraph " + pIndex;
                LocalDate pubDate = article.getPublishedAt().atZone(MARKET_ZONE).toLocalDate();
                return new PassageResponse(
                        chunk.getId(),
                        SourceType.NEWS_ARTICLE,
                        article.getId(),
                        article.getTitle(),
                        location,
                        article.getSource(),
                        pubDate,
                        chunk.getContentText(),
                        0.0);
            });
        }
        return Optional.empty();
    }
}
