package com.minhnb.finvera_be.market.operations;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.service.MarketIngestionService.IngestionResult;
import com.minhnb.finvera_be.market.service.MarketIngestionService.IngestionStatus;
import com.minhnb.finvera_be.market.service.MarketObservabilityService;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.FailureCategory;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.MarketDataset;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.MarketFailureReason;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.MarketOperation;
import com.minhnb.finvera_be.market.service.MarketOverviewService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Status;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MarketOverviewFailureTests {

    private static final Instant NOW = Instant.parse("2026-08-17T03:02:00Z");
    private static final String PRIVATE_VALUE = "integration-test-private-value";

    private SimpleMeterRegistry meters;
    private MarketObservabilityService observability;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        observability = new MarketObservabilityService(meters, Clock.fixed(NOW, ZoneOffset.UTC));
        Logger logger = (Logger) LoggerFactory.getLogger(MarketObservabilityService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(MarketObservabilityService.class);
        logger.detachAppender(appender);
        appender.stop();
        meters.close();
    }

    @Test
    void faultInjectionDistinguishesProviderAuthStaleInvalidCalculationAndDelivery() {
        observability.recordFailure(FailureCategory.SOURCE_UNAVAILABLE,
                MarketFailureReason.PROVIDER_AUTH_REQUIRED, MarketOperation.SOURCE_AUTHENTICATION);
        assertThat(observability.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);

        observability.recordFailure(FailureCategory.STALE_DATA,
                MarketFailureReason.DATA_STALE, MarketOperation.FRESHNESS_EVALUATION);
        assertThat(observability.health().getStatus().getCode()).isEqualTo("DEGRADED");

        observability.recordIngestion("FINVERA_FIXTURE", MarketDataset.INDEX,
                new IngestionResult(IndexCode.VN_INDEX, IngestionStatus.REJECTED,
                        "INVALID_INDEX_LEVEL", null, null, null), NOW.minusSeconds(90));
        assertThat(observability.health().getDetails())
                .containsEntry("category", "INVALID_SNAPSHOT")
                .containsEntry("reason", "INVALID_INDEX_LEVEL");

        observability.recordFailure(FailureCategory.CALCULATION_FAILURE,
                MarketFailureReason.REGIME_CALCULATION_FAILED, MarketOperation.REGIME_CALCULATION);
        assertThat(observability.health().getStatus()).isEqualTo(Status.DOWN);

        observability.recordFailure(FailureCategory.DELIVERY_FAILURE,
                MarketFailureReason.OVERVIEW_DELIVERY_FAILED, MarketOperation.OVERVIEW_DELIVERY);
        assertThat(meters.find("finvera.market.failures")
                .tags("category", "DELIVERY_FAILURE", "reason", "OVERVIEW_DELIVERY_FAILED",
                        "operation", "OVERVIEW_DELIVERY")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void unsafeProviderContextIsBoundedBeforeItCanReachTelemetryOrLogs() {
        observability.recordIngestion("TCBS\nAuthorization=" + PRIVATE_VALUE, MarketDataset.INDEX,
                new IngestionResult(IndexCode.VN_INDEX, IngestionStatus.REJECTED,
                        "INVALID_INDEX_LEVEL", null, null, null), NOW);

        String renderedLogs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + '\n' + right);
        assertThat(renderedLogs)
                .contains("source=UNRECOGNIZED")
                .doesNotContain(PRIVATE_VALUE)
                .doesNotContain("Authorization=");
        assertThat(meters.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
                .noneMatch(tag -> tag.getValue().contains(PRIVATE_VALUE)));
    }

    @Test
    void fixtureOverviewIsIndependentOfAiAvailability() {
        var overview = MarketOverviewService.empty(NOW);

        assertThat(overview.indices().indices()).hasSize(4);
        assertThat(overview.dataStatus().name()).isEqualTo("UNAVAILABLE");
        assertThat(MarketOverviewService.class.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(constructor.getParameterTypes())
                        .noneMatch(type -> type.getPackageName().contains("ai")));
    }
}
