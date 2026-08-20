package com.minhnb.finvera_be.analyst.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finvera.analyst")
public record AnalystProperties(
        int maxToolCalls,
        Duration toolCallTimeout,
        Duration askTimeout) {

    public AnalystProperties {
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
