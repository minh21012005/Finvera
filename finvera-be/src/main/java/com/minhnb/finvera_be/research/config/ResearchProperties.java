package com.minhnb.finvera_be.research.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finvera.research")
public record ResearchProperties(
        String internalApiKey,
        String aiServiceUrl,
        Duration ingestionTimeout,
        Duration ingestionTimeoutCheckInterval,
        long maxUploadSizeBytes) {

    /**
     * The well-known placeholder used when {@code finvera.research.internal-api-key} isn't set. Kept
     * as a shared constant so the startup warning (see {@link ResearchConfiguration}) can detect it
     * without duplicating the literal.
     */
    public static final String DEFAULT_INTERNAL_API_KEY = "dev-internal-key-change-in-prod";

    public ResearchProperties {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            internalApiKey = DEFAULT_INTERNAL_API_KEY;
        }
        if (aiServiceUrl == null || aiServiceUrl.isBlank()) {
            aiServiceUrl = "http://127.0.0.1:8000/internal/v1";
        }
        if (ingestionTimeout == null || ingestionTimeout.isZero() || ingestionTimeout.isNegative()) {
            ingestionTimeout = Duration.ofMinutes(10);
        }
        if (ingestionTimeoutCheckInterval == null || ingestionTimeoutCheckInterval.isZero()
                || ingestionTimeoutCheckInterval.isNegative()) {
            ingestionTimeoutCheckInterval = Duration.ofMinutes(1);
        }
        if (maxUploadSizeBytes <= 0) {
            maxUploadSizeBytes = 20 * 1024 * 1024L; // 20 MB default
        }
    }
}
