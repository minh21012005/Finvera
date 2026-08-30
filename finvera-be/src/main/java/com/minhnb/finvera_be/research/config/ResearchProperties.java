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
     * The placeholder earlier revisions fell back to. It is now rejected outright:
     * Constitution "Configuration" -- a missing secret must fail startup, never fall
     * back to something weak -- and a well-known literal both services would
     * silently agree on is exactly that.
     */
    static final String REJECTED_PLACEHOLDER_KEY = "dev-internal-key-change-in-prod";

    public ResearchProperties {
        if (internalApiKey == null || internalApiKey.isBlank()
                || REJECTED_PLACEHOLDER_KEY.equals(internalApiKey)) {
            throw new IllegalStateException(
                    "finvera.research.internal-api-key must be set to a real shared secret "
                    + "(FINVERA_RESEARCH_INTERNAL_API_KEY); blank and the dev placeholder are refused");
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
