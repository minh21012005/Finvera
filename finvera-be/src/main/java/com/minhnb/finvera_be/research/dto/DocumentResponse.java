package com.minhnb.finvera_be.research.dto;

import com.minhnb.finvera_be.research.domain.DocumentType;
import com.minhnb.finvera_be.research.domain.IngestionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String title,
        String symbol,
        DocumentType documentType,
        int year,
        Integer quarter,
        String source,
        LocalDate publicationDate,
        IngestionStatus ingestionStatus,
        String ingestionFailureReason,
        Instant submittedAt,
        Instant processedAt) {
}
