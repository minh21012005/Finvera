package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.entity.MarketCalendarDayEntity;
import com.minhnb.finvera_be.market.repository.MarketCalendarDayRepository;
import com.minhnb.finvera_be.market.repository.MarketIndexRepository;
import com.minhnb.finvera_be.market.repository.MarketIndexSnapshotRepository;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.market.repository.MarketSessionWindowRepository;
import com.minhnb.finvera_be.market.repository.RegimeAssessmentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketReferenceDataServiceTests {

    private MarketCalendarDayRepository calendarDays;
    private DefaultMarketReferenceDataService service;

    @BeforeEach
    void setUp() {
        MarketInstrumentRepository instruments = mock(MarketInstrumentRepository.class);
        calendarDays = mock(MarketCalendarDayRepository.class);
        MarketSessionWindowRepository sessionWindows = mock(MarketSessionWindowRepository.class);
        RegimeAssessmentRepository regimeAssessments = mock(RegimeAssessmentRepository.class);
        MarketIndexRepository indexes = mock(MarketIndexRepository.class);
        MarketIndexSnapshotRepository indexSnapshots = mock(MarketIndexSnapshotRepository.class);

        service = new DefaultMarketReferenceDataService(
                instruments, calendarDays, sessionWindows, regimeAssessments, indexes, indexSnapshots);
    }

    @Test
    void countTradingSessionsReturnsZeroForNullOrInvalidRange() {
        assertThat(service.countTradingSessionsBetween("HOSE", null, LocalDate.of(2026, 9, 1))).isEqualTo(0);
        assertThat(service.countTradingSessionsBetween("HOSE", LocalDate.of(2026, 9, 1), null)).isEqualTo(0);
        assertThat(service.countTradingSessionsBetween("HOSE", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1))).isEqualTo(0);
        assertThat(service.countTradingSessionsBetween("HOSE", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1))).isEqualTo(0);
    }

    @Test
    void countTradingSessionsExcludesWeekendsInFallback() {
        when(calendarDays.findByVenueAndTradingDateBetween(any(), any(), any())).thenReturn(List.of());

        // Friday 2026-08-21 to Monday 2026-08-24 -> only 1 session (Monday)
        LocalDate friday = LocalDate.of(2026, 8, 21);
        LocalDate monday = LocalDate.of(2026, 8, 24);
        assertThat(service.countTradingSessionsBetween("HOSE", friday, monday)).isEqualTo(1);

        // Monday to Friday same week -> 4 sessions (Tue, Wed, Thu, Fri)
        LocalDate nextFriday = LocalDate.of(2026, 8, 28);
        assertThat(service.countTradingSessionsBetween("HOSE", monday, nextFriday)).isEqualTo(4);
    }

    @Test
    void countTradingSessionsExcludesOfficialHolidaysWhenCalendarIsSeeded() {
        LocalDate friday = LocalDate.of(2026, 8, 28);
        LocalDate thursday = LocalDate.of(2026, 9, 3);

        // National Day holidays on 01/09 and 02/09 (Tue & Wed)
        MarketCalendarDayEntity holiday1 = new MarketCalendarDayEntity(
                UUID.randomUUID(), "HOSE", LocalDate.of(2026, 9, 1), false,
                "vn-exchange-calendar-v1", "UBCKNN", "HOLIDAY_NATIONAL_DAY", Instant.now());
        MarketCalendarDayEntity holiday2 = new MarketCalendarDayEntity(
                UUID.randomUUID(), "HOSE", LocalDate.of(2026, 9, 2), false,
                "vn-exchange-calendar-v1", "UBCKNN", "HOLIDAY_NATIONAL_DAY", Instant.now());

        when(calendarDays.findByVenueAndTradingDateBetween(eq("HOSE"), eq(LocalDate.of(2026, 8, 29)), eq(thursday)))
                .thenReturn(List.of(holiday1, holiday2));

        // In range (28/08, 03/09]:
        // 29/08 (Sat) -> weekend
        // 30/08 (Sun) -> weekend
        // 31/08 (Mon) -> trading session (+1)
        // 01/09 (Tue) -> holiday (skip)
        // 02/09 (Wed) -> holiday (skip)
        // 03/09 (Thu) -> trading session (+1)
        // Total: 2 trading sessions instead of 4 weekdays
        assertThat(service.countTradingSessionsBetween("HOSE", friday, thursday)).isEqualTo(2);
    }
}
