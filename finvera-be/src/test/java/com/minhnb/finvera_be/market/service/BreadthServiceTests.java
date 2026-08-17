package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.entity.MarketBreadthSnapshotEntity;
import com.minhnb.finvera_be.market.entity.MarketBreadthSnapshotInputEntity;
import com.minhnb.finvera_be.market.repository.MarketBreadthRepository;
import com.minhnb.finvera_be.market.repository.MarketBreadthSnapshotInputRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class BreadthServiceTests {
    @Test
    void persistsUniverseHashAndExactInputIdsAndMarksUnclassifiedBreadthPartial() {
        var snapshots = Mockito.mock(MarketBreadthRepository.class);
        var inputs = Mockito.mock(MarketBreadthSnapshotInputRepository.class);
        when(snapshots.save(any(MarketBreadthSnapshotEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new BreadthService(snapshots, inputs,
                Clock.fixed(Instant.parse("2026-08-17T03:05:00Z"), ZoneOffset.UTC));
        UUID instrumentId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();
        String universeHash = "a".repeat(64);

        var snapshot = service.persist(LocalDate.of(2026, 8, 17), Instant.parse("2026-08-17T03:00:00Z"), universeHash,
                new BreadthCalculator.Result(1, 1, 0, 1, 3, List.of("MISSING_REFERENCE_PRICE")),
                List.of(new BreadthService.InputLink(instrumentId, observationId, "UNCLASSIFIED", "MISSING_REFERENCE_PRICE")));

        assertThat(snapshot.dataStatus()).isEqualTo(DataStatus.PARTIAL);
        ArgumentCaptor<MarketBreadthSnapshotEntity> snapshotCaptor = ArgumentCaptor.forClass(MarketBreadthSnapshotEntity.class);
        verify(snapshots).save(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().getUniverseRevisionHash()).isEqualTo(universeHash);
        assertThat(snapshotCaptor.getValue().getUnclassified()).isEqualTo(1);
        assertThat(snapshotCaptor.getValue().getReasonCodes()).containsExactly("MISSING_REFERENCE_PRICE");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketBreadthSnapshotInputEntity>> inputCaptor = ArgumentCaptor.forClass(List.class);
        verify(inputs).saveAll(inputCaptor.capture());
        assertThat(inputCaptor.getValue()).singleElement().satisfies(link -> {
            assertThat(link.getInstrumentId()).isEqualTo(instrumentId);
            assertThat(link.getPriceObservationId()).isEqualTo(observationId);
            assertThat(link.getReasonCode()).isEqualTo("MISSING_REFERENCE_PRICE");
        });
    }
}
