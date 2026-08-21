package com.minhnb.finvera_be.analyst.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AskAnalystDto {

    private AskAnalystDto() {
    }

    public record AskAnalystRequest(
            String question,
            String symbol,
            List<PriorTurnDto> priorTurns) {
    }

    public record PriorTurnDto(
            String question,
            String answer) {
    }

    public record InternalAskRequest(
            UUID ownerId,
            String question,
            String symbol,
            List<PriorTurnDto> priorTurns) {
    }

    public record ToolCallEventDto(
            int sequenceNo,
            String toolName,
            Map<String, Object> arguments,
            String status,
            String failureReason,
            long latencyMs) {
    }

    public record InternalStructuredClaimDto(
            String claimText,
            int sequenceNo,
            String fieldPath,
            String claimedValue,
            String asOf) {
    }

    /** Matches public-api.openapi.yaml's StructuredClaim exactly: no raw fieldPath/
     * claimedValue leak — sourceField is a human-presentable label, resolved from the
     * internal fieldPath. */
    public record PublicStructuredClaimDto(
            String claimText,
            int sequenceNo,
            String toolName,
            String sourceField,
            String asOf) {
    }

    /** Internal wire shape only (finvera-ai -> finvera-be), identical to Feature 006's
     * internal Citation schema: a bare chunkId, resolved to a full public citation
     * below before ever reaching the browser. */
    public record DocumentClaimDto(
            String chunkId,
            String claimText) {
    }

    /** Matches public-api.openapi.yaml's DocumentClaim exactly — identical shape to
     * Feature 006's public Citation, resolved from DocumentClaimDto.chunkId via
     * ResearchChunkRepository/ResearchDocumentRepository/NewsArticleRepository,
     * mirroring AskService.java's own citation resolution for /research/ask. */
    public record PublicDocumentClaimDto(
            String claimText,
            String sourceType,
            String sourceId,
            String sourceTitle,
            String location,
            String source) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InternalFinalEventDto(
            String answer,
            List<InternalStructuredClaimDto> structuredClaims,
            List<DocumentClaimDto> documentClaims,
            boolean refused,
            List<ToolCallEventDto> toolCalls,
            boolean toolCallBoundReached,
            String ruleVersion) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublicFinalEventDto(
            String answer,
            List<PublicStructuredClaimDto> structuredClaims,
            List<PublicDocumentClaimDto> documentClaims,
            boolean refused,
            List<ToolCallEventDto> toolCalls,
            boolean toolCallBoundReached,
            String ruleVersion) {
    }

    public record EvidenceFactorDto(
            String factorCode,
            String description) {
    }

    public record ExplainRequest(
            String outputType,
            String symbol,
            List<EvidenceFactorDto> evidenceFactors) {
    }

    public record InternalExplainRequest(
            UUID ownerId,
            String outputType,
            String symbol,
            List<EvidenceFactorDto> evidenceFactors) {
    }

    public record InternalExplainResult(
            String explanation,
            List<String> factorsReferenced,
            boolean verified,
            String ruleVersion) {
    }

    public record ExplainResponse(
            String explanation,
            boolean verified) {
    }
}
