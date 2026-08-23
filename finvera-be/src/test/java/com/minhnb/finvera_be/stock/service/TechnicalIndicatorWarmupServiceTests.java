package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TechnicalIndicatorWarmupServiceTests {

    private final EquityProfileRepository profiles = mock(EquityProfileRepository.class);
    private final MarketReferenceDataService referenceData = mock(MarketReferenceDataService.class);
    private final EquityDailyBarRepository dailyBars = mock(EquityDailyBarRepository.class);
    private final TechnicalIndicatorService technicalIndicators = mock(TechnicalIndicatorService.class);
    private final TechnicalIndicatorWarmupService service =
            new TechnicalIndicatorWarmupService(profiles, referenceData, dailyBars, technicalIndicators);

    @Test
    void backfillsThePriorTradingDateThenTodayWhenTwoDistinctDatesExist() {
        UUID vnmId = UUID.randomUUID();
        EquityProfileEntity vnmProfile = profile(vnmId);
        when(profiles.findByEffectiveToIsNullAndListingStatus("LISTED")).thenReturn(List.of(vnmProfile));
        when(referenceData.findInstrumentsByIds(List.of(vnmId))).thenReturn(List.of(
                new InstrumentReference(vnmId, "HOSE", "VNM", "COMMON_EQUITY", "ACTIVE")));
        LocalDate today = LocalDate.of(2026, 8, 23);
        LocalDate yesterday = LocalDate.of(2026, 8, 22);
        EquityDailyBarEntity yesterdayBar = bar(vnmId, yesterday);
        EquityDailyBarEntity todayBar = bar(vnmId, today);
        when(dailyBars.findLatestNCurrentByInstrumentIdIn(List.of(vnmId), 2))
                .thenReturn(List.of(yesterdayBar, todayBar));

        var summary = service.warmUp();

        verify(technicalIndicators).findBySymbol("VNM", yesterday);
        verify(technicalIndicators).findBySymbol("VNM");
        assertThat(summary.succeeded()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(0);
    }

    @Test
    void skipsTheBackfillCallWhenFewerThanTwoDistinctTradingDatesExist() {
        UUID vnmId = UUID.randomUUID();
        EquityProfileEntity vnmProfile = profile(vnmId);
        when(profiles.findByEffectiveToIsNullAndListingStatus("LISTED")).thenReturn(List.of(vnmProfile));
        when(referenceData.findInstrumentsByIds(List.of(vnmId))).thenReturn(List.of(
                new InstrumentReference(vnmId, "HOSE", "VNM", "COMMON_EQUITY", "ACTIVE")));
        EquityDailyBarEntity onlyBar = bar(vnmId, LocalDate.of(2026, 8, 23));
        when(dailyBars.findLatestNCurrentByInstrumentIdIn(List.of(vnmId), 2)).thenReturn(List.of(onlyBar));

        service.warmUp();

        verify(technicalIndicators, never()).findBySymbol(any(), any());
        verify(technicalIndicators).findBySymbol("VNM");
    }

    @Test
    void countsOneFailureWithoutAbortingTheRestOfTheBatch() {
        UUID vnmId = UUID.randomUUID();
        UUID fptId = UUID.randomUUID();
        EquityProfileEntity vnmProfile = profile(vnmId);
        EquityProfileEntity fptProfile = profile(fptId);
        when(profiles.findByEffectiveToIsNullAndListingStatus("LISTED")).thenReturn(List.of(vnmProfile, fptProfile));
        when(referenceData.findInstrumentsByIds(List.of(vnmId, fptId))).thenReturn(List.of(
                new InstrumentReference(vnmId, "HOSE", "VNM", "COMMON_EQUITY", "ACTIVE"),
                new InstrumentReference(fptId, "HOSE", "FPT", "COMMON_EQUITY", "ACTIVE")));
        when(technicalIndicators.findBySymbol("VNM")).thenThrow(new RuntimeException("boom"));

        var summary = service.warmUp();

        verify(technicalIndicators).findBySymbol("FPT");
        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.succeeded()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
    }

    @Test
    void countsAsFailedWhenNoInstrumentReferenceIsFoundForAProfile() {
        UUID orphanId = UUID.randomUUID();
        EquityProfileEntity orphanProfile = profile(orphanId);
        when(profiles.findByEffectiveToIsNullAndListingStatus("LISTED")).thenReturn(List.of(orphanProfile));
        when(referenceData.findInstrumentsByIds(List.of(orphanId))).thenReturn(List.of());

        var summary = service.warmUp();

        verify(technicalIndicators, never()).findBySymbol(any());
        assertThat(summary.failed()).isEqualTo(1);
    }

    private static EquityProfileEntity profile(UUID instrumentId) {
        EquityProfileEntity profile = mock(EquityProfileEntity.class);
        when(profile.getInstrumentId()).thenReturn(instrumentId);
        return profile;
    }

    private static EquityDailyBarEntity bar(UUID instrumentId, LocalDate tradingDate) {
        EquityDailyBarEntity bar = mock(EquityDailyBarEntity.class);
        when(bar.getInstrumentId()).thenReturn(instrumentId);
        when(bar.getTradingDate()).thenReturn(tradingDate);
        return bar;
    }
}
