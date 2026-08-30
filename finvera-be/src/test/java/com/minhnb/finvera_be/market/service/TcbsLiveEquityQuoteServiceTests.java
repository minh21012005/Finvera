package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.entity.EquityPriceObservationEntity;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsThesisFrameMapper;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsThesisWebSocketClient;
import com.minhnb.finvera_be.market.repository.EquityPriceObservationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TcbsLiveEquityQuoteServiceTests {
    @Mock MarketReferenceDataService referenceData;
    @Mock IngestionRecordService ingestionRecords;
    @Mock EquityPriceObservationRepository prices;
    @Mock TcbsThesisWebSocketClient client;
    private TcbsLiveEquityQuoteService service;
    private final UUID instrumentId = UUID.randomUUID();
    private final UUID ingestionId = UUID.randomUUID();
    private final Instant receivedAt = Instant.parse("2026-08-24T03:05:47Z");

    @BeforeEach
    void setUp() {
        var instrument = new MarketReferenceDataService.InstrumentReference(
                instrumentId, "HOSE", "TCB", "EQUITY", "ACTIVE");
        lenient().when(referenceData.findActiveInstrumentBySymbol("TCB")).thenReturn(Optional.of(instrument));
        // Production resolveSession never returns null, so the stub must answer for
        // every instant the service asks about — accept() passes the frame's
        // receivedAt while findLatest() passes clock.instant(), one second later.
        lenient().when(referenceData.resolveSession(org.mockito.ArgumentMatchers.eq("HOSE"), any())).thenReturn(
                new MarketReferenceDataService.SessionContext(SessionState.OPEN, LocalDate.of(2026, 8, 24)));
        lenient().when(ingestionRecords.isDuplicate(any(), any(), any(), any(), any(), any())).thenReturn(false);
        lenient().when(ingestionRecords.findLatestAccepted(any(), any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(ingestionRecords.recordAccepted(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ingestionId);
        service = new TcbsLiveEquityQuoteService(referenceData, ingestionRecords, prices, client,
                Clock.fixed(Instant.parse("2026-08-24T03:05:48Z"), ZoneOffset.UTC));
    }

    @Test
    void registersOnlyActiveNormalizedSymbolsForLiveUpdates() {
        service.ensureSubscribed(" tcb ");

        verify(client).ensureEquitySubscribed("TCB");
    }

    @Test
    void rejectsInactiveOrUnknownSymbolBeforeProviderBoundary() {
        when(referenceData.findActiveInstrumentBySymbol("ZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ensureSubscribed("ZZZ"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(client, never()).ensureEquitySubscribed(any());
    }

    @Test
    void persistsMatchedPriceWithExactReferenceAndReceiveTimeBucket() {
        service.accept(new TcbsThesisFrameMapper.EquityReferenceUpdate(
                "TCB", new BigDecimal("34500"), receivedAt));
        service.accept(new TcbsThesisFrameMapper.EquityTradeUpdate(
                "TCB", new BigDecimal("35200"), new BigDecimal("700"),
                new BigDecimal("2.03"), 1_250_000L, new BigDecimal("44000000000"), receivedAt));

        ArgumentCaptor<EquityPriceObservationEntity> observation =
                ArgumentCaptor.forClass(EquityPriceObservationEntity.class);
        verify(prices).save(observation.capture());
        assertThat(observation.getValue().getInstrumentId()).isEqualTo(instrumentId);
        assertThat(observation.getValue().getMatchedOrClosePrice()).isEqualByComparingTo("35200");
        assertThat(observation.getValue().getOfficialReferencePrice()).isEqualByComparingTo("34500");
        assertThat(observation.getValue().getObservedAt()).isEqualTo(Instant.parse("2026-08-24T03:05:30Z"));
        assertThat(observation.getValue().getQualityReason()).isEqualTo("TCBS_STREAM_RECEIVE_TIME");
    }

    @Test
    void rejectsAFrameWhosePriceUnitIsInconsistentWithSessionValueAndVolume() {
        service.accept(new TcbsThesisFrameMapper.EquityReferenceUpdate(
                "TCB", new BigDecimal("34.50"), receivedAt));

        service.accept(new TcbsThesisFrameMapper.EquityTradeUpdate(
                "TCB", new BigDecimal("35.20"), new BigDecimal("0.70"),
                new BigDecimal("2.03"), 1_250_000L, new BigDecimal("44000000000"), receivedAt));

        verify(prices, never()).save(any());
    }

    @Test
    void exposesOnlyAcceptedTcbsLiveObservationAndSessionFacts() {
        var row = new EquityPriceObservationEntity(UUID.randomUUID(), instrumentId, ingestionId,
                LocalDate.of(2026, 8, 24), Instant.parse("2026-08-24T03:05:30Z"),
                new BigDecimal("35200"), new BigDecimal("34500"), null, null,
                "NOT_APPLICABLE", "TCBS_STREAM_RECEIVE_TIME");
        when(prices.findFirstByInstrumentIdOrderByObservedAtDesc(instrumentId)).thenReturn(Optional.of(row));

        service.accept(new TcbsThesisFrameMapper.EquityReferenceUpdate(
                "TCB", new BigDecimal("34500"), receivedAt));
        service.accept(new TcbsThesisFrameMapper.EquityTradeUpdate(
                "TCB", new BigDecimal("35200"), new BigDecimal("700"),
                new BigDecimal("2.03"), 1_250_000L, new BigDecimal("44000000000"), receivedAt));

        var quote = service.findLatest("tcb").orElseThrow();
        assertThat(quote.lastPrice()).isEqualByComparingTo("35200");
        assertThat(quote.referencePrice()).isEqualByComparingTo("34500");
        assertThat(quote.sessionVolume()).isEqualTo(1_250_000L);
        assertThat(quote.sessionValueVnd()).isEqualByComparingTo("44000000000");
        assertThat(quote.source()).isEqualTo("TCBS_IFLASH_THESIS");
    }

    @Test
    void referencePriceAndSessionFactsDoNotSurviveATradingDateRollover() {
        // Day 1 (2026-08-24): reference 34,500 and a trade at 35,200 build session facts.
        service.accept(new TcbsThesisFrameMapper.EquityReferenceUpdate("TCB", new BigDecimal("34500"), receivedAt));
        service.accept(new TcbsThesisFrameMapper.EquityTradeUpdate("TCB", new BigDecimal("35200"),
                new BigDecimal("700"), new BigDecimal("2.03"), 1_250_000L, new BigDecimal("44000000000"), receivedAt));

        // Day 2 (2026-08-25): the venue session rolls over; no new reference frame yet.
        Instant nextDay = Instant.parse("2026-08-25T02:15:00Z");
        lenient().when(referenceData.resolveSession(org.mockito.ArgumentMatchers.eq("HOSE"), org.mockito.ArgumentMatchers.eq(nextDay)))
                .thenReturn(new MarketReferenceDataService.SessionContext(SessionState.OPEN, LocalDate.of(2026, 8, 25)));
        org.mockito.Mockito.clearInvocations(prices);

        // A trade with no absoluteChange cannot derive a reference: yesterday's 34,500 must NOT be reused.
        service.accept(new TcbsThesisFrameMapper.EquityTradeUpdate("TCB", new BigDecimal("36000"),
                null, null, 10_000L, new BigDecimal("360000000"), nextDay));

        verify(prices, never()).save(any());
    }
}
