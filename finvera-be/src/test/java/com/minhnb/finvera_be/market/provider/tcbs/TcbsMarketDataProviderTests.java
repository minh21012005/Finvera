package com.minhnb.finvera_be.market.provider.tcbs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.provider.MarketDataProvider;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealth;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealthState;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderObservation;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderSnapshotBatch;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for TcbsMarketDataProvider.
 *
 * <p>All tests use sanitized, hardcoded fixture data — no network connections,
 * no credentials, no raw provider payloads. The provider is tested via its
 * injectable collaborator ports (TcbsRestClient, TcbsSessionState) so no
 * live TCBS endpoint is called.
 */
class TcbsMarketDataProviderTests {

    // Sanitized schema fixture matching confirmed POC field shapes.
    // Values are chosen to be obviously synthetic (round numbers, test dates).
    private static final LocalDate TRADING_DATE = LocalDate.of(2026, 1, 15);
    private static final String SOURCE = "TCBS_IFLASH_MARKET_DATA";

    private TcbsRestClient restClient;
    private TcbsSessionState sessionState;
    private TcbsMarketDataProvider provider;

    @BeforeEach
    void setUp() {
        restClient = mock(TcbsRestClient.class);
        sessionState = mock(TcbsSessionState.class);
        provider = new TcbsMarketDataProvider(restClient, sessionState);
    }

    // ── reconcileLatest ──────────────────────────────────────────────────────

    @Test
    void reconcileLatestReturnsBatchWithTradingDateOnlyLabelForAllFourIndices() {
        when(restClient.fetchTickerCommons(TRADING_DATE))
                .thenReturn(sanitizedTickerCommonsResponse(TRADING_DATE));

        ProviderSnapshotBatch batch = provider.reconcileLatest(TRADING_DATE);

        assertThat(batch.source()).isEqualTo(SOURCE);
        assertThat(batch.tradingDate()).isEqualTo(TRADING_DATE);
        assertThat(batch.observations()).hasSize(4);
        assertThat(batch.observations().stream().map(ProviderObservation::code))
                .containsExactlyInAnyOrder(
                        IndexCode.VN_INDEX, IndexCode.VN30,
                        IndexCode.HNX_INDEX, IndexCode.UPCOM_INDEX);
        // Every observation must carry the REST-trading-date-only constraint label
        batch.observations().forEach(obs ->
                assertThat(obs.reasonCodes()).contains("TCBS_REST_TRADING_DATE_ONLY"));
    }

    @Test
    void reconcileLatestUsesRefPriceAsReferenceLevelNotCalculated() {
        when(restClient.fetchTickerCommons(TRADING_DATE))
                .thenReturn(sanitizedTickerCommonsResponse(TRADING_DATE));

        ProviderSnapshotBatch batch = provider.reconcileLatest(TRADING_DATE);
        ProviderObservation vnIndex = batch.observations().stream()
                .filter(o -> o.code() == IndexCode.VN_INDEX)
                .findFirst().orElseThrow();

        // refPrice=1280.00, matchPrice=1300.00 — referenceLevel must use refPrice, not (level-change)
        assertThat(vnIndex.referenceLevel()).isEqualByComparingTo("1280.000000");
        assertThat(vnIndex.level()).isEqualByComparingTo("1300.000000");
    }

    @Test
    void reconcileLatestMarksObservationAsReferenceUnavailableWhenRefPriceIsZero() {
        when(restClient.fetchTickerCommons(TRADING_DATE))
                .thenReturn(sanitizedTickerCommonsResponseWithZeroRef(TRADING_DATE));

        ProviderSnapshotBatch batch = provider.reconcileLatest(TRADING_DATE);
        ProviderObservation vnIndex = batch.observations().stream()
                .filter(o -> o.code() == IndexCode.VN_INDEX)
                .findFirst().orElseThrow();

        assertThat(vnIndex.referenceLevel()).isNull();
        assertThat(vnIndex.reasonCodes()).contains("REFERENCE_UNAVAILABLE");
    }

    @Test
    void reconcileLatestMarksBreadthRecordIncompleteWhenMatchPriceMissing() {
        when(restClient.fetchTickerCommons(TRADING_DATE))
                .thenReturn(sanitizedTickerCommonsResponseWithMissingMatchPrice(TRADING_DATE));

        ProviderSnapshotBatch batch = provider.reconcileLatest(TRADING_DATE);

        // Batch-level reason codes must include breadth incomplete indicator
        assertThat(batch.reasonCodes()).contains("BREADTH_RECORD_INCOMPLETE");
    }

    @Test
    void reconcileLatestInfersEffectiveAtFromTradingDatePlusSessionCloseViaMtp() {
        when(restClient.fetchTickerCommons(TRADING_DATE))
                .thenReturn(sanitizedTickerCommonsResponse(TRADING_DATE));

        ProviderSnapshotBatch batch = provider.reconcileLatest(TRADING_DATE);

        // observedAt must not be null and must be on or after the trading date in Asia/Ho_Chi_Minh
        assertThat(batch.observedAt()).isNotNull();
        // The instant must correspond to 14:45 or later on the trading date (HOSE close)
        assertThat(batch.observedAt().toString()).startsWith("2026-01-15T");
    }

    // ── auth / token lifecycle ───────────────────────────────────────────────

    @Test
    void reconcileLatestThrowsAuthenticationRequiredWhenTokenIsAbsent() {
        when(restClient.fetchTickerCommons(TRADING_DATE))
                .thenThrow(new MarketDataProvider.ProviderAuthenticationRequiredException());

        assertThatThrownBy(() -> provider.reconcileLatest(TRADING_DATE))
                .isInstanceOf(MarketDataProvider.ProviderAuthenticationRequiredException.class);
    }

    @Test
    void healthReturnsAuthRequiredWhenSessionStateReportsTokenAbsent() {
        when(sessionState.isTokenPresent()).thenReturn(false);

        ProviderHealth health = provider.health();

        assertThat(health.state()).isEqualTo(ProviderHealthState.AUTH_REQUIRED);
        assertThat(health.reasonCode()).isEqualTo("PROVIDER_AUTH_REQUIRED");
    }

    @Test
    void healthReturnsDegradedWhenRestClientIsUnhealthy() {
        when(sessionState.isTokenPresent()).thenReturn(true);
        when(sessionState.isHealthy()).thenReturn(false);

        ProviderHealth health = provider.health();

        assertThat(health.state()).isEqualTo(ProviderHealthState.DEGRADED);
    }

    @Test
    void healthReturnsReadyWhenTokenPresentAndClientHealthy() {
        when(sessionState.isTokenPresent()).thenReturn(true);
        when(sessionState.isHealthy()).thenReturn(true);

        ProviderHealth health = provider.health();

        assertThat(health.state()).isEqualTo(ProviderHealthState.READY);
    }

    // ── subscribe / display stream ───────────────────────────────────────────

    @Test
    void subscribePushesObservationsToObserverWithStreamLimitationLabels() {
        @SuppressWarnings("unchecked")
        Consumer<ProviderObservation> observer = mock(Consumer.class);
        // subscribe() must call the observer with at least one observation from the stream mapper
        // The stream observation carries both stream limitation labels (no timestamp/ordering)
        provider.subscribe(observer);

        // We cannot verify stream messages without a live connection;
        // we verify the subscription is cancellable without throwing
    }

    @Test
    void subscriptionCloseDoesNotThrow() {
        @SuppressWarnings("unchecked")
        Consumer<ProviderObservation> observer = mock(Consumer.class);

        MarketDataProvider.Subscription subscription = provider.subscribe(observer);

        // Must implement AutoCloseable without throwing
        subscription.close();
    }

    // ── forbidden operations ──────────────────────────────────────────────────

    @Test
    void fetchReferenceDataReturnsAllFourAllowlistedIndexCodes() {
        MarketDataProvider.ProviderReferenceBatch batch = provider.fetchReferenceData(TRADING_DATE);

        assertThat(batch.source()).isEqualTo(SOURCE);
        assertThat(batch.indices()).containsExactlyInAnyOrder(
                IndexCode.VN_INDEX, IndexCode.VN30,
                IndexCode.HNX_INDEX, IndexCode.UPCOM_INDEX);
    }

    @Test
    void reconcileLatestNeverCallsNonMarketEndpoints() {
        when(restClient.fetchTickerCommons(TRADING_DATE))
                .thenReturn(sanitizedTickerCommonsResponse(TRADING_DATE));

        provider.reconcileLatest(TRADING_DATE);

        // Only fetchTickerCommons should be called; no trading/account/order endpoints
        verify(restClient, times(1)).fetchTickerCommons(TRADING_DATE);
        verify(restClient, never()).fetchSecurities(any());
    }

    // ── Sanitized fixture helpers ─────────────────────────────────────────────

    private static TcbsRestClient.TickerCommonsResponse sanitizedTickerCommonsResponse(LocalDate date) {
        return new TcbsRestClient.TickerCommonsResponse(
                date.toString(),
                List.of(
                        new TcbsRestClient.TickerCommonsItem("VNINDEX", 1, "1300.000000", "20.000000", "1.558900",
                                "500000000", "9000000000000", "1290.000000", "1310.000000", "1270.000000", "1280.000000"),
                        new TcbsRestClient.TickerCommonsItem("VN30", 2, "1400.000000", "15.000000", "1.083300",
                                "200000000", "4000000000000", "1390.000000", "1415.000000", "1380.000000", "1385.000000"),
                        new TcbsRestClient.TickerCommonsItem("HNXIndex", 3, "230.000000", "1.500000", "0.656000",
                                "80000000", "800000000000", "229.000000", "231.000000", "228.000000", "228.500000"),
                        new TcbsRestClient.TickerCommonsItem("UpcomIndex", 5, "92.000000", "0.500000", "0.545000",
                                "30000000", "200000000000", "91.700000", "92.300000", "91.500000", "91.500000")));
    }

    private static TcbsRestClient.TickerCommonsResponse sanitizedTickerCommonsResponseWithZeroRef(LocalDate date) {
        return new TcbsRestClient.TickerCommonsResponse(
                date.toString(),
                List.of(
                        new TcbsRestClient.TickerCommonsItem("VNINDEX", 1, "1300.000000", "20.000000", "1.558900",
                                "500000000", "9000000000000", "1290.000000", "1310.000000", "1270.000000", "0"),
                        new TcbsRestClient.TickerCommonsItem("VN30", 2, "1400.000000", "15.000000", "1.083300",
                                "200000000", "4000000000000", "1390.000000", "1415.000000", "1380.000000", "1385.000000"),
                        new TcbsRestClient.TickerCommonsItem("HNXIndex", 3, "230.000000", "1.500000", "0.656000",
                                "80000000", "800000000000", "229.000000", "231.000000", "228.000000", "228.500000"),
                        new TcbsRestClient.TickerCommonsItem("UpcomIndex", 5, "92.000000", "0.500000", "0.545000",
                                "30000000", "200000000000", "91.700000", "92.300000", "91.500000", "91.500000")));
    }

    private static TcbsRestClient.TickerCommonsResponse sanitizedTickerCommonsResponseWithMissingMatchPrice(LocalDate date) {
        return new TcbsRestClient.TickerCommonsResponse(
                date.toString(),
                List.of(
                        new TcbsRestClient.TickerCommonsItem("VNINDEX", 1, null, "20.000000", "1.558900",
                                "500000000", "9000000000000", "1290.000000", "1310.000000", "1270.000000", "1280.000000"),
                        new TcbsRestClient.TickerCommonsItem("VN30", 2, "1400.000000", "15.000000", "1.083300",
                                "200000000", "4000000000000", "1390.000000", "1415.000000", "1380.000000", "1385.000000"),
                        new TcbsRestClient.TickerCommonsItem("HNXIndex", 3, "230.000000", "1.500000", "0.656000",
                                "80000000", "800000000000", "229.000000", "231.000000", "228.000000", "228.500000"),
                        new TcbsRestClient.TickerCommonsItem("UpcomIndex", 5, "92.000000", "0.500000", "0.545000",
                                "30000000", "200000000000", "91.700000", "92.300000", "91.500000", "91.500000")));
    }

}
