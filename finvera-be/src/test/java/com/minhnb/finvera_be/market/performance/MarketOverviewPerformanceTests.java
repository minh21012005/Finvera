package com.minhnb.finvera_be.market.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.config.MarketFreshnessProperties;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.repository.MarketOverviewRepository;
import com.minhnb.finvera_be.market.service.BreadthService;
import com.minhnb.finvera_be.market.service.MarketOverviewService;
import com.minhnb.finvera_be.market.service.RegimeAssessmentService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Fixture-mode read smoke checks; not a substitute for production load testing. */
@ExtendWith(MockitoExtension.class)
class MarketOverviewPerformanceTests {

    private static final Instant NOW = Instant.parse("2026-08-17T03:00:15Z");
    private static final Duration API_P95_TARGET = Duration.ofMillis(500);
    private static final Duration FIXTURE_CONTRACTED_DELAY = Duration.ZERO;
    private static final Duration ACCEPTED_UPDATE_GRACE = Duration.ofSeconds(30);

    @Mock private MarketOverviewRepository repository;
    @Mock private BreadthService breadthService;
    @Mock private RegimeAssessmentService regimes;

    private final AtomicReference<List<MarketOverviewRepository.LatestIndexSnapshot>> accepted =
            new AtomicReference<>(rows(Instant.parse("2026-08-17T03:00:00Z"), 1));
    private MarketOverviewService service;

    @BeforeEach
    void setUp() {
        service = new MarketOverviewService(repository, breadthService, regimes,
                Clock.fixed(NOW, ZoneOffset.UTC), new MarketFreshnessProperties(FIXTURE_CONTRACTED_DELAY));
        when(repository.findLatestAcceptedIndexBoundary()).thenAnswer(ignored -> accepted.get());
        when(breadthService.latestFor(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(regimes.latestFor(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
    }

    @Test
    void coherentAcceptedOverviewReadHasFixtureP95AtOrBelowFiveHundredMilliseconds() {
        for (int warmup = 0; warmup < 5; warmup++) service.latest();

        long[] samples = new long[40];
        for (int sample = 0; sample < samples.length; sample++) {
            long started = System.nanoTime();
            var overview = service.latest();
            samples[sample] = System.nanoTime() - started;
            assertThat(overview.indices().indices()).hasSize(4);
            assertThat(overview.indices().tradingDate()).isEqualTo(LocalDate.of(2026, 8, 17));
        }

        Arrays.sort(samples);
        long p95 = samples[((samples.length * 95 + 99) / 100) - 1];
        assertThat(Duration.ofNanos(p95)).isLessThanOrEqualTo(API_P95_TARGET);
    }

    @Test
    void acceptedFixtureUpdateIsVisibleOnTheNextCoherentReadWithinConfiguredDelayPlusThirtySeconds() {
        accepted.set(rows(Instant.parse("2026-08-17T03:00:20Z"), 2));

        long started = System.nanoTime();
        var overview = service.latest();
        Duration visibility = Duration.ofNanos(System.nanoTime() - started);

        assertThat(overview.indices().observedAt()).isEqualTo(Instant.parse("2026-08-17T03:00:20Z"));
        assertThat(overview.indices().revision()).isEqualTo(2);
        assertThat(visibility).isLessThanOrEqualTo(FIXTURE_CONTRACTED_DELAY.plus(ACCEPTED_UPDATE_GRACE));
    }

    private static List<MarketOverviewRepository.LatestIndexSnapshot> rows(Instant observedAt, int revision) {
        return List.of(
                row(IndexCode.VN_INDEX, "1280.250000", observedAt, revision),
                row(IndexCode.VN30, "1342.800000", observedAt, revision),
                row(IndexCode.HNX_INDEX, "241.120000", observedAt, revision),
                row(IndexCode.UPCOM_INDEX, "98.420000", observedAt, revision));
    }

    private static MarketOverviewRepository.LatestIndexSnapshot row(IndexCode code, String level, Instant observedAt, int revision) {
        return new FixtureSnapshot(code.name(), LocalDate.of(2026, 8, 17), observedAt,
                new BigDecimal(level), new BigDecimal("1275.000000"), revision);
    }

    private record FixtureSnapshot(String indexCode, LocalDate tradingDate, Instant observedAt,
                                   BigDecimal indexLevel, BigDecimal referenceLevel, Integer revision)
            implements MarketOverviewRepository.LatestIndexSnapshot {
        @Override public String getIndexCode() { return indexCode; }
        @Override public String getDisplayName() { return indexCode; }
        @Override public String getVenue() { return "HOSE"; }
        @Override public LocalDate getTradingDate() { return tradingDate; }
        @Override public Instant getObservedAt() { return observedAt; }
        @Override public String getSessionState() { return SessionState.OPEN.name(); }
        @Override public BigDecimal getIndexLevel() { return indexLevel; }
        @Override public BigDecimal getReferenceLevel() { return referenceLevel; }
        @Override public Long getMatchedVolume() { return 1_000_000L; }
        @Override public BigDecimal getMatchedValueVnd() { return new BigDecimal("1000000000.0000"); }
        @Override public String getSource() { return "FINVERA_FIXTURE"; }
        @Override public Integer getRevision() { return revision; }
    }
}
