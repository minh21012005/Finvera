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
import com.minhnb.finvera_be.research.domain.Applicability;
import com.minhnb.finvera_be.research.domain.IngestionStatus;
import com.minhnb.finvera_be.research.domain.ResearchItemType;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NewsArticleServiceTests {

    private NewsArticleRepository newsRepository;
    private ResearchChunkRepository chunkRepository;
    private MarketReferenceDataService marketReferenceData;
    private ResearchAiClient aiClient;
    private OwnerScopedResearchAccess ownerAccess;
    private NewsArticleService newsService;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        newsRepository = mock(NewsArticleRepository.class);
        chunkRepository = mock(ResearchChunkRepository.class);
        marketReferenceData = mock(MarketReferenceDataService.class);
        aiClient = mock(ResearchAiClient.class);
        ownerAccess = mock(OwnerScopedResearchAccess.class);

        when(ownerAccess.getAuthenticatedOwnerId()).thenReturn(ownerId);

        newsService = new NewsArticleService(
                newsRepository,
                chunkRepository,
                marketReferenceData,
                aiClient,
                ownerAccess);
    }

    @Test
    void submit_news_article_creates_row_and_dispatches_ai_job() {
        SubmitNewsArticleCommand command = new SubmitNewsArticleCommand(
                "FPT công bố kết quả kinh doanh",
                "FPT",
                "VnExpress",
                "https://vnexpress.net/fpt-1",
                Instant.now(),
                "Nội dung bài viết...",
                "idem-news-1");

        UUID instrumentId = UUID.randomUUID();
        when(marketReferenceData.findInstrumentBySymbolIncludingDelisted("FPT"))
                .thenReturn(Optional.of(new InstrumentReference(instrumentId, "HSX", "FPT", "STOCK", "LISTED")));

        when(newsRepository.findByOwnerIdAndIdempotencyKey(ownerId, "idem-news-1")).thenReturn(Optional.empty());

        when(newsRepository.save(any(NewsArticleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NewsArticleResponse response = newsService.submitNewsArticle(command);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("FPT công bố kết quả kinh doanh");
        assertThat(response.ingestionStatus()).isEqualTo(IngestionStatus.PENDING);
        assertThat(response.symbol()).isEqualTo("FPT");

        verify(aiClient).submitIngestion(
                eq(response.id()),
                eq("NEWS_ARTICLE"),
                eq(ownerId),
                eq(null),
                eq(null),
                eq("Nội dung bài viết..."),
                eq("text/plain"));
    }

    @Test
    void duplicate_idempotency_key_throws_exception() {
        UUID existingId = UUID.randomUUID();
        NewsArticleEntity existing = new NewsArticleEntity(
                existingId,
                ownerId,
                "Tiêu đề cũ",
                "Nguồn",
                Instant.now(),
                "Nội dung",
                null,
                null,
                Applicability.MISSING,
                null,
                Applicability.MISSING,
                null,
                Applicability.MISSING,
                null,
                IngestionStatus.READY,
                null,
                Instant.now(),
                Instant.now(),
                "idem-replayed",
                new HashSet<>());

        when(newsRepository.findByOwnerIdAndIdempotencyKey(ownerId, "idem-replayed")).thenReturn(Optional.of(existing));

        SubmitNewsArticleCommand command = new SubmitNewsArticleCommand(
                "Tiêu đề mới",
                "FPT",
                "Nguồn",
                null,
                Instant.now(),
                "Nội dung",
                "idem-replayed");

        assertThatThrownBy(() -> newsService.submitNewsArticle(command))
                .isInstanceOf(DuplicateSubmissionException.class)
                .hasMessageContaining(existingId.toString());
    }

    @Test
    void get_news_article_not_found_throws_exception() {
        UUID articleId = UUID.randomUUID();
        when(newsRepository.findByIdAndOwnerId(articleId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newsService.getNewsArticle(articleId))
                .isInstanceOf(NewsArticleNotFoundException.class);
    }

    @Test
    void delete_news_article_cleans_up_qdrant_vectors() {
        UUID articleId = UUID.randomUUID();
        NewsArticleEntity article = new NewsArticleEntity(
                articleId,
                ownerId,
                "Tiêu đề",
                "Nguồn",
                Instant.now(),
                "Nội dung",
                null,
                null,
                Applicability.MISSING,
                null,
                Applicability.MISSING,
                null,
                Applicability.MISSING,
                null,
                IngestionStatus.READY,
                null,
                Instant.now(),
                Instant.now(),
                "idem-del",
                new HashSet<>());

        when(newsRepository.findByIdAndOwnerId(articleId, ownerId)).thenReturn(Optional.of(article));

        UUID vectorPointId = UUID.randomUUID();
        ResearchChunkEntity chunk = new ResearchChunkEntity(
                UUID.randomUUID(),
                ownerId,
                ResearchItemType.NEWS_ARTICLE,
                null,
                articleId,
                (short) 0,
                null,
                (short) 0,
                "Đoạn văn",
                "hash",
                vectorPointId,
                "v1",
                Instant.now());

        when(chunkRepository.findByNewsArticleIdAndOwnerId(articleId, ownerId)).thenReturn(List.of(chunk));

        newsService.deleteNewsArticle(articleId);

        verify(aiClient).deleteVectors(List.of(vectorPointId));
        verify(newsRepository).delete(article);
    }

    @Test
    void deleteNewsArticle_vectorCleanupFails_doesNotDeletePostgresRowAndPropagates() {
        UUID articleId = UUID.randomUUID();
        NewsArticleEntity article = new NewsArticleEntity(
                articleId,
                ownerId,
                "Tiêu đề",
                "Nguồn",
                Instant.now(),
                "Nội dung",
                null,
                null,
                Applicability.MISSING,
                null,
                Applicability.MISSING,
                null,
                Applicability.MISSING,
                null,
                IngestionStatus.READY,
                null,
                Instant.now(),
                Instant.now(),
                "idem-del-fail",
                new HashSet<>());

        when(newsRepository.findByIdAndOwnerId(articleId, ownerId)).thenReturn(Optional.of(article));

        UUID vectorPointId = UUID.randomUUID();
        ResearchChunkEntity chunk = new ResearchChunkEntity(
                UUID.randomUUID(),
                ownerId,
                ResearchItemType.NEWS_ARTICLE,
                null,
                articleId,
                (short) 0,
                null,
                (short) 0,
                "Đoạn văn",
                "hash",
                vectorPointId,
                "v1",
                Instant.now());

        when(chunkRepository.findByNewsArticleIdAndOwnerId(articleId, ownerId)).thenReturn(List.of(chunk));
        doThrow(new RuntimeException("Qdrant unreachable")).when(aiClient).deleteVectors(List.of(vectorPointId));

        assertThatThrownBy(() -> newsService.deleteNewsArticle(articleId))
                .isInstanceOf(ResearchExceptions.VectorCleanupFailedException.class);

        verify(newsRepository, never()).delete(any());
        // The still-present chunk row (with its vectorPointId) is what a retried cleanup needs.
        verify(chunkRepository, never()).delete(any());
    }
}
