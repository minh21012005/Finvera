package com.minhnb.finvera_be.research.repository;

import com.minhnb.finvera_be.research.entity.NewsArticleEntity;
import com.minhnb.finvera_be.research.entity.NewsCategory;
import com.minhnb.finvera_be.research.entity.Sentiment;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticleEntity, UUID> {

    Optional<NewsArticleEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<NewsArticleEntity> findByOwnerIdAndIdempotencyKey(UUID ownerId, String idempotencyKey);

    @Query("""
            SELECT DISTINCT a FROM NewsArticleEntity a
            LEFT JOIN a.mentionedInstrumentIds sym
            WHERE a.ownerId = :ownerId
              AND (:instrumentId IS NULL OR sym = :instrumentId)
              AND (:category IS NULL OR a.category = :category)
              AND (:sentiment IS NULL OR a.sentiment = :sentiment)
              AND (CAST(:dateFrom AS timestamp) IS NULL OR a.publishedAt >= :dateFrom)
              AND (CAST(:dateTo AS timestamp) IS NULL OR a.publishedAt <= :dateTo)
            ORDER BY a.submittedAt DESC
            """)
    Page<NewsArticleEntity> searchArticles(
            @Param("ownerId") UUID ownerId,
            @Param("instrumentId") UUID instrumentId,
            @Param("category") NewsCategory category,
            @Param("sentiment") Sentiment sentiment,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);
}
