package com.minhnb.finvera_be.research.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.research.domain.Applicability;
import com.minhnb.finvera_be.research.domain.IngestionStatus;
import com.minhnb.finvera_be.research.domain.NewsCategory;
import com.minhnb.finvera_be.research.domain.Sentiment;
import com.minhnb.finvera_be.research.dto.NewsArticlePageResponse;
import com.minhnb.finvera_be.research.dto.NewsArticleResponse;
import com.minhnb.finvera_be.research.dto.SubmitNewsArticleCommand;
import com.minhnb.finvera_be.research.entity.NewsArticleEntity;
import com.minhnb.finvera_be.research.entity.ResearchChunkEntity;
import com.minhnb.finvera_be.research.provider.ResearchAiClient;
import com.minhnb.finvera_be.research.repository.NewsArticleRepository;
import com.minhnb.finvera_be.research.repository.ResearchChunkRepository;
import com.minhnb.finvera_be.research.service.ResearchExceptions.DuplicateSubmissionException;
import com.minhnb.finvera_be.research.service.ResearchExceptions.NewsArticleNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NewsArticleService {

    private static final Logger log = LoggerFactory.getLogger(NewsArticleService.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final NewsArticleRepository newsRepository;
    private final ResearchChunkRepository chunkRepository;
    private final MarketReferenceDataService marketReferenceData;
    private final ResearchAiClient aiClient;
    private final OwnerScopedResearchAccess ownerAccess;

    public NewsArticleService(
            NewsArticleRepository newsRepository,
            ResearchChunkRepository chunkRepository,
            MarketReferenceDataService marketReferenceData,
            ResearchAiClient aiClient,
            OwnerScopedResearchAccess ownerAccess) {
        this.newsRepository = newsRepository;
        this.chunkRepository = chunkRepository;
        this.marketReferenceData = marketReferenceData;
        this.aiClient = aiClient;
        this.ownerAccess = ownerAccess;
    }

    @Transactional
    public NewsArticleResponse submitNewsArticle(SubmitNewsArticleCommand command) {
        UUID ownerId = ownerAccess.getAuthenticatedOwnerId();

        // Idempotency check before creating any row or ingestion job (research R-013)
        var existing = newsRepository.findByOwnerIdAndIdempotencyKey(ownerId, command.idempotencyKey());
        if (existing.isPresent()) {
            throw new DuplicateSubmissionException(existing.get().getId());
        }

        UUID symbolId = null;
        Set<UUID> mentionedInstrumentIds = new HashSet<>();
        if (command.symbol() != null && !command.symbol().isBlank()) {
            symbolId = marketReferenceData.findInstrumentBySymbolIncludingDelisted(command.symbol().trim().toUpperCase())
                    .map(InstrumentReference::instrumentId)
                    .orElse(null);
            if (symbolId != null) {
                mentionedInstrumentIds.add(symbolId);
            }
        }

        UUID articleId = UUID.randomUUID();

        NewsArticleEntity article = new NewsArticleEntity(
                articleId,
                ownerId,
                command.title().trim(),
                command.source().trim(),
                command.publishedAt(),
                command.body().trim(),
                command.sourceUrl() != null ? command.sourceUrl().trim() : null,
                null,
                Applicability.MISSING,
                null,
                Applicability.MISSING,
                null,
                Applicability.MISSING,
                null,
                IngestionStatus.PENDING,
                null,
                Instant.now(),
                null,
                command.idempotencyKey(),
                mentionedInstrumentIds);

        NewsArticleEntity saved = newsRepository.save(article);

        try {
            aiClient.submitIngestion(
                    saved.getId(),
                    "NEWS_ARTICLE",
                    ownerId,
                    null,
                    null,
                    command.body().trim(),
                    "text/plain");
        } catch (Exception e) {
            log.error("Failed to dispatch ingestion job for news article {}: {}", saved.getId(), e.getMessage());
            // Stays PENDING, timeout reaper will handle if never processed
        }

        return toResponse(saved, command.symbol());
    }

    @Transactional(readOnly = true)
    public NewsArticleResponse getNewsArticle(UUID newsArticleId) {
        UUID ownerId = ownerAccess.getAuthenticatedOwnerId();
        NewsArticleEntity article = newsRepository.findByIdAndOwnerId(newsArticleId, ownerId)
                .orElseThrow(() -> new NewsArticleNotFoundException(newsArticleId));

        String symbol = resolveSymbol(article.getMentionedInstrumentIds());
        return toResponse(article, symbol);
    }

    @Transactional(readOnly = true)
    public NewsArticlePageResponse listNewsArticles(
            String symbol,
            NewsCategory category,
            Sentiment sentiment,
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

        Instant fromInstant = (dateFrom != null) ? dateFrom.atStartOfDay(MARKET_ZONE).toInstant() : null;
        Instant toInstant = (dateTo != null) ? dateTo.plusDays(1).atStartOfDay(MARKET_ZONE).toInstant() : null;

        int pageNumber = offset / Math.max(1, limit);
        var page = newsRepository.searchArticles(
                ownerId,
                symbolId,
                category,
                sentiment,
                fromInstant,
                toInstant,
                PageRequest.of(pageNumber, limit));

        List<NewsArticleResponse> items = page.getContent().stream()
                .map(a -> toResponse(a, resolveSymbol(a.getMentionedInstrumentIds())))
                .toList();

        return new NewsArticlePageResponse(items, page.getTotalElements(), limit, offset);
    }

    @Transactional
    public void deleteNewsArticle(UUID newsArticleId) {
        UUID ownerId = ownerAccess.getAuthenticatedOwnerId();
        NewsArticleEntity article = newsRepository.findByIdAndOwnerId(newsArticleId, ownerId)
                .orElseThrow(() -> new NewsArticleNotFoundException(newsArticleId));

        // Gather chunk vector IDs for Qdrant vector cleanup (DATA-004, FR-016)
        List<UUID> vectorIds = chunkRepository.findByNewsArticleIdAndOwnerId(newsArticleId, ownerId).stream()
                .map(ResearchChunkEntity::getVectorPointId)
                .toList();

        if (!vectorIds.isEmpty()) {
            try {
                aiClient.deleteVectors(vectorIds);
            } catch (Exception e) {
                // Fail closed: do not delete the Postgres row if vector cleanup failed, or the
                // research_chunk.vectorPointId rows (the only record of what to clean up) would be
                // lost and those vectors would become permanently unreachable orphans (DATA-004/SC-006).
                log.error("Failed to delete vectors for news article {}: {}", newsArticleId, e.getMessage());
                throw new ResearchExceptions.VectorCleanupFailedException(
                        "Failed to delete vectors for news article " + newsArticleId, e);
            }
        }

        newsRepository.delete(article);
    }

    private String resolveSymbol(Set<UUID> instrumentIds) {
        if (instrumentIds == null || instrumentIds.isEmpty()) {
            return null;
        }
        return marketReferenceData.findInstrumentsByIds(List.copyOf(instrumentIds)).stream()
                .findFirst()
                .map(InstrumentReference::symbol)
                .orElse(null);
    }

    private NewsArticleResponse toResponse(NewsArticleEntity a, String symbol) {
        return new NewsArticleResponse(
                a.getId(),
                a.getTitle(),
                symbol,
                a.getSource(),
                a.getReferenceUrl(),
                a.getPublishedAt(),
                a.getCategory(),
                a.getCategoryApplicability(),
                a.getSentiment(),
                a.getSentimentApplicability(),
                a.getImpactLevel(),
                a.getImpactApplicability(),
                a.getSector(),
                a.getIngestionStatus(),
                a.getIngestionFailureReason(),
                a.getSubmittedAt(),
                a.getProcessedAt());
    }
}
