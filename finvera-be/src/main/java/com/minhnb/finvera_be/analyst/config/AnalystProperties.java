package com.minhnb.finvera_be.analyst.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finvera.analyst")
public record AnalystProperties(
        String aiServiceUrl,
        String internalApiKey,
        int maxToolCalls,
        Duration toolCallTimeout,
        Duration askTimeout,
        int conversationContextCandidates,
        int conversationContextIncluded,
        int conversationContextCharacters,
        Duration conversationStaleGrace) {

    public AnalystProperties {
        if (aiServiceUrl == null || aiServiceUrl.isBlank()) {
            aiServiceUrl = "http://127.0.0.1:8000";
        }
        if (internalApiKey == null || internalApiKey.isBlank()
                || "dev-internal-key-change-in-prod".equals(internalApiKey)) {
            // Constitution "Configuration": a missing secret fails startup; it never
            // falls back to a well-known placeholder.
            throw new IllegalStateException(
                    "finvera.analyst.internal-api-key must be set to a real shared secret "
                    + "(FINVERA_ANALYST_INTERNAL_API_KEY); blank and the dev placeholder are refused");
        }
        if (maxToolCalls <= 0) {
            maxToolCalls = 10;
        }
        if (toolCallTimeout == null || toolCallTimeout.isZero() || toolCallTimeout.isNegative()) {
            toolCallTimeout = Duration.ofSeconds(10);
        }
        if (askTimeout == null || askTimeout.isZero() || askTimeout.isNegative()) {
            askTimeout = Duration.ofSeconds(30);
        }
        if (conversationContextCandidates <= 0 || conversationContextCandidates > 10) {
            conversationContextCandidates = 10;
        }
        if (conversationContextIncluded <= 0 || conversationContextIncluded > 5
                || conversationContextIncluded > conversationContextCandidates) {
            conversationContextIncluded = Math.min(5, conversationContextCandidates);
        }
        if (conversationContextCharacters <= 0 || conversationContextCharacters > 12_000) {
            conversationContextCharacters = 12_000;
        }
        if (conversationStaleGrace == null || conversationStaleGrace.isNegative()) {
            conversationStaleGrace = Duration.ofSeconds(60);
        }
    }
}
