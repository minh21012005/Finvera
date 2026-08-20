package com.minhnb.finvera_be.research.provider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AiInternalDto {

    private AiInternalDto() {
    }

    public record IngestionAcceptedResponse(
            UUID researchItemId,
            String status,
            Instant submittedAt) {
    }

    public record RetrieveChunksRequest(
            String query,
            UUID ownerId,
            String symbol,
            String documentType,
            String newsCategory,
            String dateFrom,
            String dateTo,
            int topK) {
    }

    public record RankedChunkDto(
            UUID chunkId,
            UUID vectorPointId,
            double score,
            double vectorScore,
            double recencyScore,
            double filterScore,
            String itemType,
            UUID researchItemId,
            String symbol,
            String documentType,
            String newsCategory,
            Instant publishedAt) {
    }

    public record RetrieveChunksResponse(
            List<RankedChunkDto> chunks) {
    }

    public record DeleteVectorsRequest(
            List<UUID> vectorPointIds) {
    }

    public record SynthesizeContextChunk(
            UUID chunkId,
            int blockIndex,
            String itemType,
            String title,
            String source,
            String publicationDate,
            String contentText) {
    }

    public record SynthesizeRequest(
            String question,
            UUID ownerId,
            List<SynthesizeContextChunk> contextChunks) {
    }

    public record InternalCitation(
            UUID chunkId,
            String claimText) {
    }

    public record InternalAnswerResult(
            String answer,
            List<InternalCitation> citations,
            boolean refused,
            String refusalReason) {
    }
}
