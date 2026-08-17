package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.service.MarketIngestionService.IngestionResult;
import com.minhnb.finvera_be.market.service.MarketIngestionService.IngestionStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Service;

@Service
public class MarketObservabilityService implements HealthIndicator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketObservabilityService.class);
    private static final Pattern SAFE_SOURCE = Pattern.compile("[A-Z0-9_-]{1,64}");
    private static final String DEGRADED = "DEGRADED";

    private final MeterRegistry meters;
    private final Clock clock;
    private final AtomicReference<FailureSignal> latestFailure = new AtomicReference<>();

    public MarketObservabilityService(MeterRegistry meters, Clock clock) {
        this.meters = meters;
        this.clock = clock;
    }

    public void recordIngestion(
            String source,
            MarketDataset dataset,
            IngestionResult result,
            Instant observedAt) {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(observedAt, "observedAt");

        String safeSource = safeSourceLabel(source);
        MarketFailureReason reason = MarketFailureReason.fromCode(result.reasonCode());
        Counter.builder("finvera.market.ingestion")
                .description("Normalized market observations by bounded outcome")
                .tags("dataset", dataset.name(), "outcome", result.status().name(),
                        "reason", reason.name())
                .register(meters)
                .increment();

        long lagSeconds = Math.max(0, Duration.between(observedAt, clock.instant()).toSeconds());
        DistributionSummary.builder("finvera.market.ingestion.lag")
                .description("Seconds between source observation and ingestion completion")
                .baseUnit("seconds")
                .tag("dataset", dataset.name())
                .register(meters)
                .record(lagSeconds);

        if (result.status() == IngestionStatus.REJECTED) {
            publishFailure(FailureCategory.INVALID_SNAPSHOT, reason,
                    MarketOperation.INGESTION_VALIDATION, clock.instant());
            LOGGER.warn("market_ingestion source={} dataset={} subject={} outcome={} reason={} observed_at={}",
                    safeSource, dataset, result.code(), result.status(), reason, observedAt);
            return;
        }

        LOGGER.info("market_ingestion source={} dataset={} subject={} outcome={} reason={} observed_at={}",
                safeSource, dataset, result.code(), result.status(), reason, observedAt);
    }

    public void recordFailure(
            FailureCategory category,
            MarketFailureReason reason,
            MarketOperation operation) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(operation, "operation");
        Instant occurredAt = clock.instant();
        publishFailure(category, reason, operation, occurredAt);
        LOGGER.warn("market_failure category={} reason={} operation={} occurred_at={}",
                category, reason, operation, occurredAt);
    }

    public void markHealthy() {
        latestFailure.set(null);
    }

    @Override
    public Health health() {
        FailureSignal signal = latestFailure.get();
        if (signal == null) {
            return Health.up().withDetail("state", "READY").build();
        }

        Health.Builder builder = switch (signal.category()) {
            case SOURCE_UNAVAILABLE -> Health.outOfService();
            case STALE_DATA, INVALID_SNAPSHOT -> Health.status(DEGRADED);
            case CALCULATION_FAILURE, DELIVERY_FAILURE -> Health.down();
        };
        return builder
                .withDetail("category", signal.category().name())
                .withDetail("reason", signal.reason().name())
                .withDetail("operation", signal.operation().name())
                .withDetail("occurredAt", signal.occurredAt().toString())
                .build();
    }

    static String safeSourceLabel(String source) {
        if (source == null) {
            return "UNRECOGNIZED";
        }
        String normalized = source.toUpperCase(Locale.ROOT);
        return SAFE_SOURCE.matcher(normalized).matches() ? normalized : "UNRECOGNIZED";
    }

    private void publishFailure(
            FailureCategory category,
            MarketFailureReason reason,
            MarketOperation operation,
            Instant occurredAt) {
        latestFailure.set(new FailureSignal(category, reason, operation, occurredAt));
        Counter.builder("finvera.market.failures")
                .description("Market failures by bounded operational taxonomy")
                .tags("category", category.name(), "reason", reason.name(),
                        "operation", operation.name())
                .register(meters)
                .increment();
    }

    public enum FailureCategory {
        SOURCE_UNAVAILABLE,
        STALE_DATA,
        INVALID_SNAPSHOT,
        CALCULATION_FAILURE,
        DELIVERY_FAILURE
    }

    public enum MarketDataset {
        INDEX,
        EQUITY_PRICE,
        BREADTH,
        REGIME
    }

    public enum MarketOperation {
        SOURCE_AUTHENTICATION,
        SOURCE_CONNECTIVITY,
        FRESHNESS_EVALUATION,
        INGESTION_VALIDATION,
        BREADTH_CALCULATION,
        REGIME_CALCULATION,
        OVERVIEW_DELIVERY
    }

    public enum MarketFailureReason {
        NONE,
        PROVIDER_AUTH_REQUIRED,
        PROVIDER_CONNECTIVITY_FAILED,
        DATA_DELAYED,
        DATA_STALE,
        OUT_OF_ORDER,
        MISSING_INDEX_LEVEL,
        INVALID_INDEX_LEVEL,
        MISSING_REFERENCE_LEVEL,
        INVALID_REFERENCE_LEVEL,
        INVALID_MATCHED_VOLUME,
        INVALID_MATCHED_VALUE,
        INVALID_DECIMAL_PRECISION,
        INVALID_PERCENTAGE_CHANGE,
        INDEX_REFERENCE_NOT_FOUND,
        BREADTH_CALCULATION_FAILED,
        REGIME_CALCULATION_FAILED,
        OVERVIEW_DELIVERY_FAILED,
        UNEXPECTED_FAILURE;

        static MarketFailureReason fromCode(String code) {
            if (code == null || code.isBlank()) {
                return NONE;
            }
            try {
                return valueOf(code);
            } catch (IllegalArgumentException exception) {
                return UNEXPECTED_FAILURE;
            }
        }
    }

    private record FailureSignal(
            FailureCategory category,
            MarketFailureReason reason,
            MarketOperation operation,
            Instant occurredAt) {
    }
}
