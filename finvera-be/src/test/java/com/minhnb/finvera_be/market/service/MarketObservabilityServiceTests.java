package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.service.MarketIngestionService.IngestionResult;
import com.minhnb.finvera_be.market.service.MarketIngestionService.IngestionStatus;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.FailureCategory;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.MarketDataset;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.MarketFailureReason;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.MarketOperation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class MarketObservabilityServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-17T03:02:00Z");

    private SimpleMeterRegistry meters;
    private MarketObservabilityService observability;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        observability = new MarketObservabilityService(
                meters, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void invalidSnapshotEmitsBoundedMetricsLagAndDegradedHealth() {
        observability.recordIngestion(
                "FINVERA_FIXTURE",
                MarketDataset.INDEX,
                new IngestionResult(IndexCode.VN_INDEX, IngestionStatus.REJECTED,
                        "INVALID_INDEX_LEVEL", null, null, null),
                NOW.minusSeconds(120));

        assertThat(meters.find("finvera.market.ingestion")
                .tags("dataset", "INDEX", "outcome", "REJECTED",
                        "reason", "INVALID_INDEX_LEVEL")
                .counter().count()).isEqualTo(1);
        assertThat(meters.find("finvera.market.ingestion.lag")
                .tag("dataset", "INDEX").summary().max()).isEqualTo(120);
        assertThat(observability.health().getStatus().getCode()).isEqualTo("DEGRADED");
        assertThat(observability.health().getDetails())
                .containsEntry("category", "INVALID_SNAPSHOT")
                .containsEntry("reason", "INVALID_INDEX_LEVEL");
    }

    @Test
    void failureTaxonomyDistinguishesSourceStaleCalculationAndDeliveryFailures() {
        observability.recordFailure(FailureCategory.SOURCE_UNAVAILABLE,
                MarketFailureReason.PROVIDER_CONNECTIVITY_FAILED,
                MarketOperation.SOURCE_CONNECTIVITY);
        assertThat(observability.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);

        observability.recordFailure(FailureCategory.STALE_DATA,
                MarketFailureReason.DATA_STALE,
                MarketOperation.FRESHNESS_EVALUATION);
        assertThat(observability.health().getStatus().getCode()).isEqualTo("DEGRADED");

        observability.recordFailure(FailureCategory.CALCULATION_FAILURE,
                MarketFailureReason.REGIME_CALCULATION_FAILED,
                MarketOperation.REGIME_CALCULATION);
        assertThat(observability.health().getStatus()).isEqualTo(Status.DOWN);

        observability.recordFailure(FailureCategory.DELIVERY_FAILURE,
                MarketFailureReason.OVERVIEW_DELIVERY_FAILED,
                MarketOperation.OVERVIEW_DELIVERY);
        assertThat(meters.find("finvera.market.failures")
                .tags("category", "DELIVERY_FAILURE",
                        "reason", "OVERVIEW_DELIVERY_FAILED",
                        "operation", "OVERVIEW_DELIVERY")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void telemetryBoundaryCannotAcceptRawPayloadsCredentialsOrThrowableMessages() {
        assertThat(MarketObservabilityService.safeSourceLabel(
                "TCBS\nAuthorization=private-value")).isEqualTo("UNRECOGNIZED");

        Class<?>[] forbiddenTypes = {byte[].class, char[].class, Throwable.class, Map.class};
        assertThat(Arrays.stream(MarketObservabilityService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getParameterTypes)
                .flatMap(Arrays::stream))
                .noneMatch(type -> Arrays.stream(forbiddenTypes)
                        .anyMatch(forbidden -> forbidden.isAssignableFrom(type)));
    }
}
