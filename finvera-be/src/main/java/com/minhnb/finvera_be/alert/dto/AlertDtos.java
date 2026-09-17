package com.minhnb.finvera_be.alert.dto;
import tools.jackson.databind.JsonNode;import jakarta.validation.constraints.*;import java.time.Instant;import java.util.*;
public final class AlertDtos{private AlertDtos(){}
 public record CreateAlertRequest(@NotBlank@Size(max=120)String name,@NotNull JsonNode condition){}
 public record SetAlertStateRequest(boolean enabled){}
 public record AlertResponse(UUID id,String name,JsonNode condition,String conditionVersion,String conditionSummary,boolean enabled,String episodeState,EvaluationResponse latestEvaluation,Instant lastTriggeredAt,String lastDeliveryOutcome,Instant createdAt,Instant updatedAt){}
 public record EvaluationResponse(UUID id,String outcome,String reasonCode,Instant factAt,Instant acceptedAt,Instant evaluatedAt,int attemptCount,JsonNode evidence){}
 public record NotificationResponse(UUID id,UUID alertId,String title,String message,JsonNode conditionSnapshot,JsonNode evidence,Instant triggeredAt,Instant deliveredAt,Instant readAt){}
 public record PageResponse<T>(List<T> items,long totalCount,int limit,int offset){}
 public record UnreadCountResponse(long count){}public record ReadAllResponse(int updatedCount,Instant readAt){}
}
