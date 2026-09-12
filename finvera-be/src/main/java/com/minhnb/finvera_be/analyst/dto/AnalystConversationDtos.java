package com.minhnb.finvera_be.analyst.dto;

import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.PublicFinalEventDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AnalystConversationDtos {
    private AnalystConversationDtos() { }

    public record ConversationAskRequest(
            UUID conversationId,
            @NotNull UUID clientRequestId,
            @NotBlank @Size(max = 2000) String question,
            @Size(min = 1, max = 20) @Pattern(regexp = "[A-Za-z0-9._-]+") String symbol) { }

    public record ContextWindowInfo(String ruleVersion, int includedExchanges, int omittedExchanges) { }
    public record AcceptedEvent(String type, UUID conversationId, UUID exchangeId, boolean created,
            String title, ContextWindowInfo contextWindow) { }
    public record RenameConversationRequest(@NotBlank @Size(max = 120) String title) { }
    public record ConversationSummary(UUID id, String title, String titleSource, Instant createdAt,
            Instant updatedAt, Instant lastActivityAt, long exchangeCount, boolean processing) { }
    public record ConversationPage(List<ConversationSummary> items, String nextCursor, boolean hasMore) { }
    public record ConversationExchange(UUID id, long sequenceNo, String question, String symbol, String status,
            @com.fasterxml.jackson.annotation.JsonProperty("final") PublicFinalEventDto finalResult,
            String failureCode, ContextWindowInfo contextWindow,
            Instant createdAt, Instant completedAt) { }
    public record ExchangePage(ConversationSummary conversation, List<ConversationExchange> items,
            String olderCursor, boolean hasMore) { }
}
