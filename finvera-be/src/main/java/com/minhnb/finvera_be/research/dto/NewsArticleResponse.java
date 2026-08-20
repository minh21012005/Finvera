package com.minhnb.finvera_be.research.dto;

import com.minhnb.finvera_be.research.domain.Applicability;
import com.minhnb.finvera_be.research.domain.ImpactLevel;
import com.minhnb.finvera_be.research.domain.IngestionStatus;
import com.minhnb.finvera_be.research.domain.NewsCategory;
import com.minhnb.finvera_be.research.domain.Sentiment;
import java.time.Instant;
import java.util.UUID;

public record NewsArticleResponse(
        UUID id,
        String title,
        String symbol,
        String source,
        String sourceUrl,
        Instant publishedAt,
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
        Instant processedAt) {
}
