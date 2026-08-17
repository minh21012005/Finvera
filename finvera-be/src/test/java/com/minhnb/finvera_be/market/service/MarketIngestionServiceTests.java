package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.entity.MarketIndexEntity;
import com.minhnb.finvera_be.market.entity.MarketIndexSnapshotEntity;
import com.minhnb.finvera_be.market.entity.MarketObservationEntity;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderObservation;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderSnapshotBatch;
import com.minhnb.finvera_be.market.repository.MarketIndexRepository;
import com.minhnb.finvera_be.market.repository.MarketIndexSnapshotRepository;
import com.minhnb.finvera_be.market.repository.MarketObservationRepository;
import com.minhnb.finvera_be.market.service.MarketIngestionService.IngestionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketIngestionServiceTests {

    private static final LocalDate TRADING_DATE = LocalDate.of(2026, 8, 17);
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-17T03:00:00Z");
    private static final UUID INDEX_ID = UUID.fromString("00000000-0000-0000-0001-000000000001");

    @Mock
    private MarketObservationRepository observations;
    @Mock
    private MarketIndexSnapshotRepository snapshots;
    @Mock
    private MarketIndexRepository indices;
    @Mock
    private MarketObservabilityService observability;

    private MarketIngestionService service;

    @BeforeEach
    void setUp() {
        service = new MarketIngestionService(observations, snapshots, indices, observability,
                Clock.fixed(Instant.parse("2026-08-17T03:02:00Z"), ZoneOffset.UTC));
    }

    @Test
    void duplicateIsIdempotentAndDoesNotAppendAnotherSnapshot() {
        when(observations.existsBySourceAndDatasetAndSubjectKeyAndTradingDateAndObservedAtAndPayloadHash(
                anyString(), anyString(), anyString(), any(), any(), anyString())).thenReturn(true);

        var result = service.ingest(batch(observation("1280.250000", "1275.000000", null, null)));

        assertThat(result.results().getFirst().status()).isEqualTo(IngestionStatus.DUPLICATE);
        verify(observations, never()).save(any());
        verify(snapshots, never()).save(any());
    }

    @Test
    void outOfOrderObservationIsAuditedButCannotRegressTheLatestSnapshot() {
        when(observations.existsBySourceAndDatasetAndSubjectKeyAndTradingDateAndObservedAtAndPayloadHash(
                anyString(), anyString(), anyString(), any(), any(), anyString())).thenReturn(false);
        when(observations.findFirstBySourceAndDatasetAndSubjectKeyAndTradingDateAndStatusOrderByObservedAtDescIngestedAtDesc(
                "FINVERA_FIXTURE", "INDEX", "VN_INDEX", TRADING_DATE, "ACCEPTED"))
                .thenReturn(Optional.of(acceptedObservation(
                        UUID.randomUUID(), OBSERVED_AT.plusSeconds(60), "a".repeat(64), null)));
        when(observations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.ingest(batch(observation("1280.250000", "1275.000000", null, null)));

        var captured = ArgumentCaptor.forClass(MarketObservationEntity.class);
        verify(observations).save(captured.capture());
        assertThat(result.results().getFirst().reasonCode()).isEqualTo("OUT_OF_ORDER");
        assertThat(captured.getValue().getStatus()).isEqualTo("REJECTED");
        assertThat(captured.getValue().getReasonCode()).isEqualTo("OUT_OF_ORDER");
        verify(snapshots, never()).save(any());
    }

    @Test
    void correctionAppendsLinkedObservationAndSnapshotRevision() {
        when(indices.findByCode(IndexCode.VN_INDEX.name())).thenReturn(Optional.of(index()));
        UUID oldObservationId = UUID.randomUUID();
        UUID oldSnapshotId = UUID.randomUUID();
        when(observations.existsBySourceAndDatasetAndSubjectKeyAndTradingDateAndObservedAtAndPayloadHash(
                anyString(), anyString(), anyString(), any(), any(), anyString())).thenReturn(false);
        when(observations.findFirstBySourceAndDatasetAndSubjectKeyAndTradingDateAndStatusOrderByObservedAtDescIngestedAtDesc(
                "FINVERA_FIXTURE", "INDEX", "VN_INDEX", TRADING_DATE, "ACCEPTED"))
                .thenReturn(Optional.of(acceptedObservation(
                        oldObservationId, OBSERVED_AT, "a".repeat(64), null)));
        when(observations.findFirstBySourceAndDatasetAndSubjectKeyAndTradingDateAndObservedAtAndStatusOrderByIngestedAtDesc(
                "FINVERA_FIXTURE", "INDEX", "VN_INDEX", TRADING_DATE, OBSERVED_AT, "ACCEPTED"))
                .thenReturn(Optional.of(acceptedObservation(
                        oldObservationId, OBSERVED_AT, "a".repeat(64), null)));
        when(snapshots.findFirstByIndexIdAndTradingDateAndObservedAtOrderByRevisionDesc(
                INDEX_ID, TRADING_DATE, OBSERVED_AT))
                .thenReturn(Optional.of(snapshot(oldSnapshotId, oldObservationId, 1)));
        when(observations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.ingest(batch(observation("1280.300000", "1275.000000", null, null)));

        var ingestion = ArgumentCaptor.forClass(MarketObservationEntity.class);
        var derived = ArgumentCaptor.forClass(MarketIndexSnapshotEntity.class);
        verify(observations).save(ingestion.capture());
        verify(snapshots).save(derived.capture());
        assertThat(result.results().getFirst().status()).isEqualTo(IngestionStatus.CORRECTED);
        assertThat(ingestion.getValue().getSupersedesId()).isEqualTo(oldObservationId);
        assertThat(derived.getValue().getRevision()).isEqualTo(2);
        assertThat(derived.getValue().getSupersedesId()).isEqualTo(oldSnapshotId);
        assertThat(derived.getValue().getAbsoluteChange()).isEqualByComparingTo("5.300000");
    }

    @Test
    void invalidNumberIsAuditedWithoutCreatingDerivedFactsOrSubstitutingZero() {
        when(observations.existsBySourceAndDatasetAndSubjectKeyAndTradingDateAndObservedAtAndPayloadHash(
                anyString(), anyString(), anyString(), any(), any(), anyString())).thenReturn(false);
        when(observations.findFirstBySourceAndDatasetAndSubjectKeyAndTradingDateAndStatusOrderByObservedAtDescIngestedAtDesc(
                anyString(), anyString(), anyString(), any(), anyString())).thenReturn(Optional.empty());
        when(observations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.ingest(batch(observation("-0.000001", "1275.000000", null, null)));

        var captured = ArgumentCaptor.forClass(MarketObservationEntity.class);
        verify(observations).save(captured.capture());
        assertThat(result.results().getFirst().status()).isEqualTo(IngestionStatus.REJECTED);
        assertThat(result.results().getFirst().reasonCode()).isEqualTo("INVALID_INDEX_LEVEL");
        assertThat(captured.getValue().getStatus()).isEqualTo("REJECTED");
        verify(snapshots, never()).save(any());
    }

    @Test
    void delayedCorrectionOfOlderObservationIsVersionedWithoutBeingRejectedAsOutOfOrder() {
        UUID correctedObservationId = UUID.randomUUID();
        UUID correctedSnapshotId = UUID.randomUUID();
        when(indices.findByCode(IndexCode.VN_INDEX.name())).thenReturn(Optional.of(index()));
        when(observations.existsBySourceAndDatasetAndSubjectKeyAndTradingDateAndObservedAtAndPayloadHash(
                anyString(), anyString(), anyString(), any(), any(), anyString())).thenReturn(false);
        when(observations.findFirstBySourceAndDatasetAndSubjectKeyAndTradingDateAndObservedAtAndStatusOrderByIngestedAtDesc(
                "FINVERA_FIXTURE", "INDEX", "VN_INDEX", TRADING_DATE, OBSERVED_AT, "ACCEPTED"))
                .thenReturn(Optional.of(acceptedObservation(
                        correctedObservationId, OBSERVED_AT, "a".repeat(64), null)));
        when(observations.findFirstBySourceAndDatasetAndSubjectKeyAndTradingDateAndStatusOrderByObservedAtDescIngestedAtDesc(
                "FINVERA_FIXTURE", "INDEX", "VN_INDEX", TRADING_DATE, "ACCEPTED"))
                .thenReturn(Optional.of(acceptedObservation(
                        UUID.randomUUID(), OBSERVED_AT.plusSeconds(60), "b".repeat(64), null)));
        when(snapshots.findFirstByIndexIdAndTradingDateAndObservedAtOrderByRevisionDesc(
                INDEX_ID, TRADING_DATE, OBSERVED_AT))
                .thenReturn(Optional.of(snapshot(correctedSnapshotId, correctedObservationId, 1)));
        when(observations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.ingest(batch(observation("1280.300000", "1275.000000", null, null)));

        assertThat(result.results().getFirst().status()).isEqualTo(IngestionStatus.CORRECTED);
        assertThat(result.results().getFirst().revision()).isEqualTo(2);
    }

    @Test
    void normalizedDecimalRetryProducesTheSameHashAndOnlyOneAcceptedSnapshot() {
        when(indices.findByCode(IndexCode.VN_INDEX.name())).thenReturn(Optional.of(index()));
        var storedHash = new AtomicReference<String>();
        when(observations.existsBySourceAndDatasetAndSubjectKeyAndTradingDateAndObservedAtAndPayloadHash(
                anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(5).equals(storedHash.get()));
        when(observations.findFirstBySourceAndDatasetAndSubjectKeyAndTradingDateAndStatusOrderByObservedAtDescIngestedAtDesc(
                anyString(), anyString(), anyString(), any(), anyString())).thenReturn(Optional.empty());
        when(observations.save(any())).thenAnswer(invocation -> {
            MarketObservationEntity entity = invocation.getArgument(0);
            storedHash.set(entity.getPayloadHash());
            return entity;
        });
        when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var first = service.ingest(batch(observation("1280.25", "1275", null, null)));
        var retry = service.ingest(batch(observation("1280.2500000", "1275.000000", null, null)));

        assertThat(first.results().getFirst().status()).isEqualTo(IngestionStatus.ACCEPTED);
        assertThat(retry.results().getFirst().status()).isEqualTo(IngestionStatus.DUPLICATE);
        verify(snapshots).save(any());
    }

    private static ProviderSnapshotBatch batch(ProviderObservation observation) {
        return new ProviderSnapshotBatch("FINVERA_FIXTURE", TRADING_DATE, OBSERVED_AT,
                SessionState.OPEN, DataStatus.CURRENT, List.of(), List.of(observation));
    }

    private static ProviderObservation observation(
            String level, String reference, Long volume, String value) {
        return new ProviderObservation(IndexCode.VN_INDEX, decimal(level), decimal(reference),
                null, null, volume, decimal(value), List.of());
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static MarketIndexEntity index() {
        return new MarketIndexEntity(INDEX_ID, "VN_INDEX", "VNINDEX", "VN-Index", "HOSE",
                LocalDate.of(2000, 7, 28), null);
    }

    private static MarketObservationEntity acceptedObservation(
            UUID id, Instant observedAt, String hash, UUID supersedesId) {
        return new MarketObservationEntity(id, "FINVERA_FIXTURE", "INDEX", "VN_INDEX", TRADING_DATE,
                observedAt, observedAt, observedAt.plusSeconds(1), null, hash,
                "ACCEPTED", null, supersedesId);
    }

    private static MarketIndexSnapshotEntity snapshot(UUID id, UUID ingestionId, int revision) {
        return new MarketIndexSnapshotEntity(id, INDEX_ID, ingestionId, TRADING_DATE, OBSERVED_AT,
                OBSERVED_AT.plusSeconds(revision), "OPEN", new BigDecimal("1280.250000"),
                new BigDecimal("1275.000000"), new BigDecimal("5.250000"),
                new BigDecimal("0.411765"), null, null, "FINVERA_FIXTURE", revision, null);
    }
}
