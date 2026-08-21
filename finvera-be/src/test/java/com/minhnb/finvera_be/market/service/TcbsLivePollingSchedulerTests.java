package com.minhnb.finvera_be.market.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.provider.MarketDataProvider;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderAuthenticationRequiredException;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealth;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealthState;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderSnapshotBatch;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.FailureCategory;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.MarketFailureReason;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.MarketOperation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies every tick degrades gracefully instead of throwing (Constitution Principle VII). */
class TcbsLivePollingSchedulerTests {

    private MarketDataProvider provider;
    private MarketIngestionService ingestion;
    private MarketObservabilityService observability;
    private TcbsLivePollingScheduler scheduler;

    @BeforeEach
    void setUp() {
        provider = mock(MarketDataProvider.class);
        ingestion = mock(MarketIngestionService.class);
        observability = mock(MarketObservabilityService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-15T02:00:00Z"), ZoneOffset.UTC);
        scheduler = new TcbsLivePollingScheduler(provider, ingestion, observability, clock);
    }

    @Test
    void skipsAndRecordsFailureWhenProviderReportsAuthRequired() {
        when(provider.health()).thenReturn(new ProviderHealth(ProviderHealthState.AUTH_REQUIRED, "PROVIDER_AUTH_REQUIRED"));

        scheduler.poll();

        verify(provider, never()).reconcileLatest(any());
        verify(ingestion, never()).ingest(any());
        verify(observability).recordFailure(FailureCategory.SOURCE_UNAVAILABLE,
                MarketFailureReason.PROVIDER_AUTH_REQUIRED, MarketOperation.SOURCE_AUTHENTICATION);
    }

    @Test
    void skipsAndRecordsFailureWhenProviderIsDegraded() {
        when(provider.health()).thenReturn(new ProviderHealth(ProviderHealthState.DEGRADED, "TCBS_REST_UNHEALTHY"));

        scheduler.poll();

        verify(provider, never()).reconcileLatest(any());
        verify(observability).recordFailure(FailureCategory.SOURCE_UNAVAILABLE,
                MarketFailureReason.PROVIDER_CONNECTIVITY_FAILED, MarketOperation.SOURCE_AUTHENTICATION);
    }

    @Test
    void ingestsTheReconciledBatchWhenProviderIsReady() {
        when(provider.health()).thenReturn(new ProviderHealth(ProviderHealthState.READY, "READY"));
        ProviderSnapshotBatch batch = new ProviderSnapshotBatch(
                "TCBS_IFLASH_MARKET_DATA", java.time.LocalDate.of(2026, 1, 15), Instant.now(),
                SessionState.CLOSED, DataStatus.CURRENT, List.of(), List.of());
        when(provider.reconcileLatest(eq(java.time.LocalDate.of(2026, 1, 15)))).thenReturn(batch);

        scheduler.poll();

        verify(ingestion, times(1)).ingest(batch);
        verify(observability, never()).recordFailure(any(), any(), any());
    }

    @Test
    void aReconciliationFailureIsRecordedAndNeverThrown() {
        when(provider.health()).thenReturn(new ProviderHealth(ProviderHealthState.READY, "READY"));
        when(provider.reconcileLatest(any())).thenThrow(new ProviderAuthenticationRequiredException());

        scheduler.poll();

        verify(ingestion, never()).ingest(any());
        verify(observability).recordFailure(FailureCategory.SOURCE_UNAVAILABLE,
                MarketFailureReason.PROVIDER_AUTH_REQUIRED, MarketOperation.SOURCE_AUTHENTICATION);
    }

    @Test
    void anUnexpectedRuntimeExceptionIsRecordedAsConnectivityFailureAndNeverThrown() {
        when(provider.health()).thenReturn(new ProviderHealth(ProviderHealthState.READY, "READY"));
        when(provider.reconcileLatest(any())).thenThrow(new RuntimeException("boom"));

        scheduler.poll();

        verify(observability).recordFailure(FailureCategory.SOURCE_UNAVAILABLE,
                MarketFailureReason.PROVIDER_CONNECTIVITY_FAILED, MarketOperation.SOURCE_CONNECTIVITY);
    }
}
