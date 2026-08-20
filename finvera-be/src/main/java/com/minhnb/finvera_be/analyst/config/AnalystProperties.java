package com.minhnb.finvera_be.analyst.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finvera.analyst")
public record AnalystProperties(
        String aiServiceUrl,
        String internalApiKey,
        int maxToolCalls,
        Duration toolCallTimeout,
        Duration askTimeout) {

    public AnalystProperties {
        if (aiServiceUrl == null || aiServiceUrl.isBlank()) {
            aiServiceUrl = "http://127.0.0.1:8000";
        }
        if (internalApiKey == null || internalApiKey.isBlank()) {
            internalApiKey = "dev-internal-key-change-in-prod";
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
    }
}
