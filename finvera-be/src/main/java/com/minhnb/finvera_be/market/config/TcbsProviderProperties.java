package com.minhnb.finvera_be.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Live TCBS iFlash connection settings. {@code apiKey} is an owner secret sourced from the
 * environment (never committed, never logged, never echoed to a client). Binding stays valid
 * with a blank key in fixture/import mode; {@code TcbsHttpSessionState} rejects a blank key only
 * when a renewal is actually attempted. {@code pollIntervalMs} is also read directly by
 * {@code TcbsLivePollingScheduler}'s {@code @Scheduled} annotation (which needs a property
 * placeholder, not an injected value), so it is kept as a plain millisecond value here too.
 */
@Validated
@ConfigurationProperties("finvera.market.provider.tcbs")
public record TcbsProviderProperties(String baseUrl, String apiKey, long pollIntervalMs) {
    public TcbsProviderProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        if (pollIntervalMs <= 0) {
            throw new IllegalArgumentException("pollIntervalMs must be positive");
        }
    }
}
