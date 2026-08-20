package com.minhnb.finvera_be.research.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finvera.research")
public record ResearchProperties(
        String internalApiKey,
        String aiServiceUrl,
        Duration ingestionTimeout,
        long maxUploadSizeBytes) {

    public ResearchProperties {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            internalApiKey = "dev-internal-key-change-in-prod";
        }
        if (aiServiceUrl == null || aiServiceUrl.isBlank()) {
            aiServiceUrl = "http://127.0.0.1:8000/internal/v1";
        }
        if (ingestionTimeout == null || ingestionTimeout.isZero() || ingestionTimeout.isNegative()) {
            ingestionTimeout = Duration.ofMinutes(10);
        }
        if (maxUploadSizeBytes <= 0) {
            maxUploadSizeBytes = 20 * 1024 * 1024L; // 20 MB default
        }
    }
}
