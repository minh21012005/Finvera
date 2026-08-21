package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.provider.MarketDataProvider;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderAuthenticationRequiredException;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealthState;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.FailureCategory;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.MarketFailureReason;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.MarketOperation;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the live TCBS index snapshot on a fixed interval and ingests it (Constitution Principle
 * VII: bounded external calls with an explicit failure path that never blocks other request
 * processing). Every tick is caught end-to-end: a degraded/auth-required provider is recorded as
 * a failure signal and skipped, never thrown out of the scheduled method, so one bad tick cannot
 * stop the poller or crash the application.
 */
@Component
@ConditionalOnProperty(name = "finvera.market.provider.mode", havingValue = "live")
@ConditionalOnProperty(name = "finvera.market.provider.live-enabled", havingValue = "true")
public class TcbsLivePollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(TcbsLivePollingScheduler.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MarketDataProvider provider;
    private final MarketIngestionService ingestion;
    private final MarketObservabilityService observability;
    private final Clock clock;

    public TcbsLivePollingScheduler(
            MarketDataProvider provider,
            MarketIngestionService ingestion,
            MarketObservabilityService observability,
            Clock clock) {
        this.provider = provider;
        this.ingestion = ingestion;
        this.observability = observability;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${finvera.market.provider.tcbs.poll-interval-ms:60000}")
    public void poll() {
        try {
            MarketDataProvider.ProviderHealth health = provider.health();
            if (health.state() != ProviderHealthState.READY) {
                MarketFailureReason reason = health.state() == ProviderHealthState.AUTH_REQUIRED
                        ? MarketFailureReason.PROVIDER_AUTH_REQUIRED
                        : MarketFailureReason.PROVIDER_CONNECTIVITY_FAILED;
                observability.recordFailure(
                        FailureCategory.SOURCE_UNAVAILABLE, reason, MarketOperation.SOURCE_AUTHENTICATION);
                return;
            }
            LocalDate tradingDate = LocalDate.now(clock.withZone(MARKET_ZONE));
            var batch = provider.reconcileLatest(tradingDate);
            ingestion.ingest(batch);
        } catch (ProviderAuthenticationRequiredException e) {
            observability.recordFailure(FailureCategory.SOURCE_UNAVAILABLE,
                    MarketFailureReason.PROVIDER_AUTH_REQUIRED, MarketOperation.SOURCE_AUTHENTICATION);
        } catch (Exception e) {
            log.warn("TCBS live poll failed: {}", e.getClass().getSimpleName());
            observability.recordFailure(FailureCategory.SOURCE_UNAVAILABLE,
                    MarketFailureReason.PROVIDER_CONNECTIVITY_FAILED, MarketOperation.SOURCE_CONNECTIVITY);
        }
    }
}
