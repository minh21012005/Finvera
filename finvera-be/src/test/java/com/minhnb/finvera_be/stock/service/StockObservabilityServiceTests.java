package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionResult;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import com.minhnb.finvera_be.stock.service.StockObservabilityService.FailureCategory;
import com.minhnb.finvera_be.stock.service.StockObservabilityService.StockDataset;
import com.minhnb.finvera_be.stock.service.StockObservabilityService.StockFailureReason;
import com.minhnb.finvera_be.stock.service.StockObservabilityService.StockOperation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StockObservabilityServiceTests {

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
    private final StockObservabilityService observability =
            new StockObservabilityService(new SimpleMeterRegistry(), clock);

    @Test
    void distinguishesEachOfTheSixFailureCategoriesInHealth() {
        for (FailureCategory category : FailureCategory.values()) {
            observability.recordFailure(category, StockFailureReason.UNEXPECTED_FAILURE, StockOperation.CALCULATION);
            var health = observability.health();
            assertThat(health.getDetails()).containsEntry("category", category.name());
        }
        assertThat(FailureCategory.values()).hasSize(6);
    }

    @Test
    void healthReturnsToReadyAfterMarkHealthy() {
        observability.recordFailure(FailureCategory.PROVIDER_UNAVAILABLE, StockFailureReason.PROVIDER_CONNECTIVITY_FAILED,
                StockOperation.SOURCE_CONNECTIVITY);
        assertThat(observability.health().getDetails()).containsKey("category");

        observability.markHealthy();
        assertThat(observability.health().getDetails()).containsEntry("state", "READY");
    }

    @Test
    void rejectedIngestionIsRecordedAsAFailureWithNoSensitivePayload() {
        var result = new IngestionResult(IngestionStatus.REJECTED, "UNKNOWN_INSTRUMENT", null, null);
        observability.recordIngestion("FINVERA_FIXTURE", StockDataset.CHART, result, clock.instant());
        var health = observability.health();
        assertThat(health.getDetails().get("reason")).isEqualTo("UNKNOWN_INSTRUMENT");
        assertThat(health.getDetails().values()).noneMatch(value -> value.toString().toLowerCase().contains("token")
                || value.toString().toLowerCase().contains("password"));
    }

    @Test
    void unrecognizedSourceLabelsAreRedactedRatherThanLogged() {
        assertThat(StockObservabilityService.safeSourceLabel("api_key=super-secret-value"))
                .isEqualTo("UNRECOGNIZED");
        assertThat(StockObservabilityService.safeSourceLabel("TCBS")).isEqualTo("TCBS");
    }
}
