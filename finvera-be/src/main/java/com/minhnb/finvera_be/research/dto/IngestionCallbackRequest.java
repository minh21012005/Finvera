package com.minhnb.finvera_be.research.dto;

import java.util.List;
import java.util.UUID;

public record IngestionCallbackRequest(
        String status,
        String failureReason,
        String extractedText,
        List<IngestedChunkDto> chunks,
        IngestedNewsClassificationDto classification) {

    public record IngestedChunkDto(
            UUID id,
            short chunkIndex,
            Short pageNumber,
            Short paragraphIndex,
            String contentText,
            String contentHash,
            UUID vectorPointId,
            String embeddingVersion) {
    }

    public record IngestedNewsClassificationDto(
            String category,
            String categoryApplicability,
            String sentiment,
            String sentimentApplicability,
            String impactLevel,
            String impactApplicability,
            String sector,
            List<String> mentionedSymbols) {
    }
}
