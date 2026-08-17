package com.minhnb.finvera_be.market.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Contracted provider delay is deployment configuration, never inferred by the read path. */
@Validated
@ConfigurationProperties("finvera.market.freshness")
public record MarketFreshnessProperties(@NotNull Duration indexContractedDelay) {
    public MarketFreshnessProperties {
        if (indexContractedDelay.isNegative()) {
            throw new IllegalArgumentException("indexContractedDelay must not be negative");
        }
    }
}
