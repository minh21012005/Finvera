package com.minhnb.finvera_be.positioning.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Metrics intentionally accept bounded categories only, never identifiers or financial values. */
@Component
public class PositionSizingMetrics {
    private static final Set<String> MODES = Set.of("MANUAL", "PORTFOLIO", "UNKNOWN");
    private static final Set<String> OUTCOMES = Set.of("CALCULATED", "WITHHELD", "INVALID", "FAILED");
    private static final Set<String> REASONS = Set.of("NONE", "INVALID_REQUEST", "INVALID_INPUT",
            "INVALID_RISK_BUDGET", "INCOMPLETE_COST_POLICY", "INVALID_PRICE_RELATIONSHIP",
            "MARKET_LOT_RULE_UNAVAILABLE", "PORTFOLIO_DATA_UNAVAILABLE", "SIGNAL_NOT_CURRENT",
            "SIGNAL_NOT_CONFIRMED", "BELOW_STANDARD_LOT", "SERVER_ERROR");
    private final MeterRegistry registry;
    public PositionSizingMetrics(MeterRegistry registry) { this.registry = registry; }
    public Timer.Sample start() { return Timer.start(registry); }
    public void finish(Timer.Sample sample, String mode, String outcome, String reason) {
        String safeMode = MODES.contains(mode) ? mode : "UNKNOWN";
        String safeOutcome = OUTCOMES.contains(outcome) ? outcome : "FAILED";
        String safeReason = REASONS.contains(reason) ? reason : "INVALID_INPUT";
        sample.stop(registry.timer("finvera.position_sizing.duration", "mode", safeMode, "outcome", safeOutcome,
                "reason", safeReason, "version", "position-sizing-v1"));
        registry.counter("finvera.position_sizing.outcomes", "mode", safeMode, "outcome", safeOutcome,
                "reason", safeReason, "version", "position-sizing-v1").increment();
    }
}
