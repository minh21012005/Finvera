package com.minhnb.finvera_be.research.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PassageResponse(
        SourceType sourceType,
        UUID sourceId,
        String sourceTitle,
        String location,
        String source,
        LocalDate publicationDate,
        String excerpt,
        double score) {
}
