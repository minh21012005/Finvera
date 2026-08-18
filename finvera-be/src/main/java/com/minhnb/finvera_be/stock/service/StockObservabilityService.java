package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionResult;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
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

/**
 * Privacy-safe metrics, health, and the six-class failure taxonomy from
 * plan.md's Observability section (NFR-006): provider unavailable, provider
 * auth expired, invalid/rejected record, insufficient accepted history,
 * calculation failure, delivery failure.
 */
@Service
public class StockObservabilityService implements HealthIndicator {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockObservabilityService.class);
    private static final Pattern SAFE_SOURCE = Pattern.compile("[A-Z0-9_-]{1,64}");
    private static final String DEGRADED = "DEGRADED";

    private final MeterRegistry meters;
    private final Clock clock;
    private final AtomicReference<FailureSignal> latestFailure = new AtomicReference<>();

    public StockObservabilityService(MeterRegistry meters, Clock clock) {
        this.meters = meters;
        this.clock = clock;
    }

    public void recordIngestion(String source, StockDataset dataset, IngestionResult result, Instant observedAt) {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(observedAt, "observedAt");

        String safeSource = safeSourceLabel(source);
        StockFailureReason reason = StockFailureReason.fromCode(result.reasonCode());
        Counter.builder("finvera.stock.ingestion")
                .description("Normalized stock records by bounded outcome")
                .tags("dataset", dataset.name(), "outcome", result.status().name(), "reason", reason.name())
                .register(meters)
                .increment();

        long lagSeconds = Math.max(0, Duration.between(observedAt, clock.instant()).toSeconds());
        DistributionSummary.builder("finvera.stock.ingestion.lag")
                .description("Seconds between source observation and ingestion completion")
                .baseUnit("seconds")
                .tag("dataset", dataset.name())
                .register(meters)
                .record(lagSeconds);

        if (result.status() == IngestionStatus.REJECTED) {
            publishFailure(FailureCategory.INVALID_RECORD, reason, StockOperation.INGESTION_VALIDATION, clock.instant());
            LOGGER.warn("stock_ingestion source={} dataset={} outcome={} reason={} observed_at={}",
                    safeSource, dataset, result.status(), reason, observedAt);
            return;
        }
        LOGGER.info("stock_ingestion source={} dataset={} outcome={} reason={} observed_at={}",
                safeSource, dataset, result.status(), reason, observedAt);
    }

    public void recordCalculation(StockDataset dataset, String indicatorOrRuleCode, boolean published, String reasonCode) {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(indicatorOrRuleCode, "indicatorOrRuleCode");
        StockFailureReason reason = StockFailureReason.fromCode(reasonCode);
        Counter.builder("finvera.stock.calculation")
                .description("Technical/valuation calculations by bounded outcome")
                .tags("dataset", dataset.name(), "code", indicatorOrRuleCode,
                        "outcome", published ? "PUBLISHED" : "WITHHELD", "reason", reason.name())
                .register(meters)
                .increment();
        if (!published) {
            publishFailure(FailureCategory.INSUFFICIENT_HISTORY, reason, StockOperation.CALCULATION, clock.instant());
        }
    }

    public void recordFailure(FailureCategory category, StockFailureReason reason, StockOperation operation) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(operation, "operation");
        Instant occurredAt = clock.instant();
        publishFailure(category, reason, operation, occurredAt);
        LOGGER.warn("stock_failure category={} reason={} operation={} occurred_at={}",
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
            case PROVIDER_UNAVAILABLE -> Health.outOfService();
            case PROVIDER_AUTH_EXPIRED, INSUFFICIENT_HISTORY, INVALID_RECORD -> Health.status(DEGRADED);
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
            FailureCategory category, StockFailureReason reason, StockOperation operation, Instant occurredAt) {
        latestFailure.set(new FailureSignal(category, reason, operation, occurredAt));
        Counter.builder("finvera.stock.failures")
                .description("Stock failures by the six-class NFR-006 taxonomy")
                .tags("category", category.name(), "reason", reason.name(), "operation", operation.name())
                .register(meters)
                .increment();
    }

    /** The six failure classes NFR-006 requires to stay distinguishable. */
    public enum FailureCategory {
        PROVIDER_UNAVAILABLE,
        PROVIDER_AUTH_EXPIRED,
        INVALID_RECORD,
        INSUFFICIENT_HISTORY,
        CALCULATION_FAILURE,
        DELIVERY_FAILURE
    }

    public enum StockDataset {
        OVERVIEW, CHART, TECHNICAL, FUNDAMENTALS, VALUATION, CORPORATE_ACTION
    }

    public enum StockOperation {
        SOURCE_AUTHENTICATION,
        SOURCE_CONNECTIVITY,
        FRESHNESS_EVALUATION,
        INGESTION_VALIDATION,
        CALCULATION,
        SECTION_DELIVERY
    }

    public enum StockFailureReason {
        NONE,
        PROVIDER_AUTH_REQUIRED,
        PROVIDER_CONNECTIVITY_FAILED,
        UNKNOWN_INSTRUMENT,
        INVALID_OHLC,
        VALUE_OUT_OF_BOUNDS,
        INVALID_PERIOD,
        DUPLICATE,
        OUT_OF_ORDER,
        PAYLOAD_REJECTED,
        SOURCE_CONFLICT,
        INSUFFICIENT_HISTORY,
        FUNDAMENTALS_STALE,
        NO_COMPARISON_BASIS,
        CALCULATION_FAILED,
        SECTION_DELIVERY_FAILED,
        UNEXPECTED_FAILURE;

        static StockFailureReason fromCode(String code) {
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
            FailureCategory category, StockFailureReason reason, StockOperation operation, Instant occurredAt) {
    }
}
