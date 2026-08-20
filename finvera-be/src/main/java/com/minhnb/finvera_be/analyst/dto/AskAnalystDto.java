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

    public record PublicStructuredClaimDto(
            String claimText,
            String toolName,
            String sourceField,
            String claimedValue,
            String asOf) {
    }

    public record DocumentClaimDto(
            String chunkId,
            String claimText,
            String title,
            String source,
            Integer pageNumber) {
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
            List<DocumentClaimDto> documentClaims,
            boolean refused,
            List<ToolCallEventDto> toolCalls,
            boolean toolCallBoundReached,
            String ruleVersion) {
    }
}
