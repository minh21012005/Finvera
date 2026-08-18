package com.minhnb.finvera_be.stock.operations;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionResult;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import com.minhnb.finvera_be.stock.service.StockObservabilityService;
import com.minhnb.finvera_be.stock.service.StockObservabilityService.FailureCategory;
import com.minhnb.finvera_be.stock.service.StockObservabilityService.StockDataset;
import com.minhnb.finvera_be.stock.service.StockObservabilityService.StockFailureReason;
import com.minhnb.finvera_be.stock.service.StockObservabilityService.StockOperation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Status;

/**
 * T067 [FR-012, NFR-004, NFR-006, NFR-007]
 * Fault-injection and safe telemetry tests for stock detail operations.
 */
class StockDetailFailureTests {

    private static final Instant NOW = Instant.parse("2026-08-17T03:02:00Z");
    private static final String PRIVATE_TOKEN = "secret-provider-token-12345";

    private SimpleMeterRegistry meters;
    private StockObservabilityService observability;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        observability = new StockObservabilityService(meters, Clock.fixed(NOW, ZoneOffset.UTC));
        Logger logger = (Logger) LoggerFactory.getLogger(StockObservabilityService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(StockObservabilityService.class);
        logger.detachAppender(appender);
        appender.stop();
        meters.close();
    }

    @Test
    void faultInjectionDistinguishesAllSixFailureClasses() {
        // 1. Source unavailable / auth required
        observability.recordFailure(FailureCategory.PROVIDER_UNAVAILABLE,
                StockFailureReason.PROVIDER_AUTH_REQUIRED, StockOperation.SOURCE_AUTHENTICATION);
        assertThat(observability.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);

        // 2. Insufficient accepted history
        observability.recordFailure(FailureCategory.INSUFFICIENT_HISTORY,
                StockFailureReason.INSUFFICIENT_HISTORY, StockOperation.CALCULATION);
        assertThat(observability.health().getStatus().getCode()).isEqualTo("DEGRADED");

        // 3. Provider auth expired
        observability.recordFailure(FailureCategory.PROVIDER_AUTH_EXPIRED,
                StockFailureReason.PROVIDER_AUTH_REQUIRED, StockOperation.SOURCE_AUTHENTICATION);
        assertThat(observability.health().getStatus().getCode()).isEqualTo("DEGRADED");

        // 4. Ingestion rejection / invalid record
        observability.recordIngestion("FINVERA_FIXTURE", StockDataset.FUNDAMENTALS,
                new IngestionResult(IngestionStatus.REJECTED, "INVALID_PERIOD", null, null), NOW.minusSeconds(60));
        assertThat(observability.health().getDetails())
                .containsEntry("category", "INVALID_RECORD")
                .containsEntry("reason", "INVALID_PERIOD");

        // 5. Calculation failure
        observability.recordFailure(FailureCategory.CALCULATION_FAILURE,
                StockFailureReason.CALCULATION_FAILED, StockOperation.CALCULATION);
        assertThat(observability.health().getStatus()).isEqualTo(Status.DOWN);

        // 6. Delivery failure
        observability.recordFailure(FailureCategory.DELIVERY_FAILURE,
                StockFailureReason.SECTION_DELIVERY_FAILED, StockOperation.SECTION_DELIVERY);
        assertThat(meters.find("finvera.stock.failures")
                .tags("category", "DELIVERY_FAILURE", "reason", "SECTION_DELIVERY_FAILED",
                        "operation", "SECTION_DELIVERY")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void safeTelemetryRedactsCredentialsAndPrivatePayloadsFromLogsAndMetrics() {
        observability.recordIngestion("TCBS\nAuthorization=" + PRIVATE_TOKEN, StockDataset.CHART,
                new IngestionResult(IngestionStatus.REJECTED, "INVALID_OHLC", null, null), NOW);

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + '\n' + right);

        assertThat(logs)
                .contains("source=UNRECOGNIZED")
                .doesNotContain(PRIVATE_TOKEN)
                .doesNotContain("Authorization=");

        assertThat(meters.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
                .noneMatch(tag -> tag.getValue().contains(PRIVATE_TOKEN)));
    }
}
