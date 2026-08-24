package com.minhnb.finvera_be.market.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("finvera.market.live-overlay.tcbs")
public record TcbsThesisProperties(
        boolean enabled,
        String baseUrl,
        String websocketUrl,
        String apiKey,
        Duration heartbeatInterval,
        Duration reconnectMaxDelay,
        Integer maxDynamicSymbols) {

    private static final String DEFAULT_BASE_URL = "https://openapi.tcbs.com.vn";
    private static final String DEFAULT_WEBSOCKET_URL =
            "wss://openapi.tcbs.com.vn/ws/thesis/v1/stream/normal";

    public TcbsThesisProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        websocketUrl = websocketUrl == null || websocketUrl.isBlank() ? DEFAULT_WEBSOCKET_URL : websocketUrl;
        heartbeatInterval = heartbeatInterval == null ? Duration.ofSeconds(2) : heartbeatInterval;
        reconnectMaxDelay = reconnectMaxDelay == null ? Duration.ofSeconds(30) : reconnectMaxDelay;
        if (heartbeatInterval.isNegative() || heartbeatInterval.isZero()) {
            throw new IllegalArgumentException("heartbeatInterval must be positive");
        }
        if (reconnectMaxDelay.isNegative() || reconnectMaxDelay.isZero()) {
            throw new IllegalArgumentException("reconnectMaxDelay must be positive");
        }
        maxDynamicSymbols = maxDynamicSymbols == null ? 100 : maxDynamicSymbols;
        if (maxDynamicSymbols <= 0) {
            throw new IllegalArgumentException("maxDynamicSymbols must be positive");
        }
    }
}
