package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorResultEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorResultRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TechnicalIndicatorWarmupServiceTests {

    private final EquityProfileRepository profiles = mock(EquityProfileRepository.class);
    private final MarketReferenceDataService referenceData = mock(MarketReferenceDataService.class);
    private final EquityDailyBarRepository dailyBars = mock(EquityDailyBarRepository.class);
    private final TechnicalIndicatorResultRepository indicatorResults = mock(TechnicalIndicatorResultRepository.class);
    private final TechnicalIndicatorService technicalIndicators = mock(TechnicalIndicatorService.class);
    private final TechnicalIndicatorWarmupService service = new TechnicalIndicatorWarmupService(
            profiles, referenceData, dailyBars, indicatorResults, technicalIndicators);

    @Test
    void bootstrapsAnInstrumentWithNoIndicatorHistoryUsingOnlyItsLatestBars() {
        UUID vnmId = UUID.randomUUID();
        givenListedInstrument(vnmId, "VNM");
        when(indicatorResults.findByInstrumentIdInAndRuleVersionAndCurrentTrue(List.of(vnmId),
                TechnicalIndicatorsV1.RULE_VERSION)).thenReturn(List.of());
        LocalDate day1 = LocalDate.of(2026, 8, 20);
        LocalDate day2 = LocalDate.of(2026, 8, 21);
        EquityDailyBarEntity bar1 = bar(vnmId, day1);
        EquityDailyBarEntity bar2 = bar(vnmId, day2);
        when(dailyBars.findLatestNCurrentByInstrumentIdIn(List.of(vnmId), 30)).thenReturn(List.of(bar1, bar2));

        var summary = service.warmUp();

        verify(technicalIndicators).findBySymbol("VNM", day1);
        verify(technicalIndicators).findBySymbol("VNM");
        assertThat(summary.succeeded()).isEqualTo(1);
    }

    @Test
    void backfillsEveryTradingDayMissedSinceALongGapNotJustTheLatestTwo() {
        UUID vnmId = UUID.randomUUID();
        givenListedInstrument(vnmId, "VNM");
        LocalDate lastComputed = LocalDate.of(2026, 8, 21);
        TechnicalIndicatorResultEntity previousRow = mock(TechnicalIndicatorResultEntity.class);
        when(previousRow.getInstrumentId()).thenReturn(vnmId);
        when(previousRow.getAsOfTradingDate()).thenReturn(lastComputed);
        when(indicatorResults.findByInstrumentIdInAndRuleVersionAndCurrentTrue(List.of(vnmId),
                TechnicalIndicatorsV1.RULE_VERSION)).thenReturn(List.of(previousRow));

        LocalDate day24 = LocalDate.of(2026, 8, 24);
        LocalDate day25 = LocalDate.of(2026, 8, 25);
        LocalDate day26 = LocalDate.of(2026, 8, 26);
        LocalDate day27 = LocalDate.of(2026, 8, 27);
        EquityDailyBarEntity barLast = bar(vnmId, lastComputed);
        EquityDailyBarEntity bar24 = bar(vnmId, day24);
        EquityDailyBarEntity bar25 = bar(vnmId, day25);
        EquityDailyBarEntity bar26 = bar(vnmId, day26);
        EquityDailyBarEntity bar27 = bar(vnmId, day27);
        when(dailyBars.findLatestNCurrentByInstrumentIdIn(List.of(vnmId), 30))
                .thenReturn(List.of(barLast, bar24, bar25, bar26, bar27));

        service.warmUp();

        verify(technicalIndicators).findBySymbol("VNM", day24);
        verify(technicalIndicators).findBySymbol("VNM", day25);
        verify(technicalIndicators).findBySymbol("VNM", day26);
        verify(technicalIndicators, never()).findBySymbol("VNM", day27);
        verify(technicalIndicators).findBySymbol("VNM");
        verify(technicalIndicators, never()).findBySymbol("VNM", lastComputed);
    }

    @Test
    void doesNothingExtraWhenAlreadyUpToDate() {
        UUID vnmId = UUID.randomUUID();
        givenListedInstrument(vnmId, "VNM");
        LocalDate latest = LocalDate.of(2026, 8, 21);
        TechnicalIndicatorResultEntity previousRow = mock(TechnicalIndicatorResultEntity.class);
        when(previousRow.getInstrumentId()).thenReturn(vnmId);
        when(previousRow.getAsOfTradingDate()).thenReturn(latest);
        when(indicatorResults.findByInstrumentIdInAndRuleVersionAndCurrentTrue(List.of(vnmId),
                TechnicalIndicatorsV1.RULE_VERSION)).thenReturn(List.of(previousRow));
        EquityDailyBarEntity latestBar = bar(vnmId, latest);
        when(dailyBars.findLatestNCurrentByInstrumentIdIn(List.of(vnmId), 30)).thenReturn(List.of(latestBar));

        service.warmUp();

        verify(technicalIndicators, never()).findBySymbol(any(), any());
        verify(technicalIndicators).findBySymbol("VNM");
    }

    @Test
    void countsOneFailureWithoutAbortingTheRestOfTheBatch() {
        UUID vnmId = UUID.randomUUID();
        UUID fptId = UUID.randomUUID();
        givenListedInstrument(vnmId, "VNM", fptId, "FPT");
        when(indicatorResults.findByInstrumentIdInAndRuleVersionAndCurrentTrue(List.of(vnmId, fptId),
                TechnicalIndicatorsV1.RULE_VERSION)).thenReturn(List.of());
        when(dailyBars.findLatestNCurrentByInstrumentIdIn(List.of(vnmId, fptId), 30)).thenReturn(List.of());
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

    private void givenListedInstrument(UUID instrumentId, String symbol) {
        EquityProfileEntity entity = profile(instrumentId);
        when(profiles.findByEffectiveToIsNullAndListingStatus("LISTED")).thenReturn(List.of(entity));
        when(referenceData.findInstrumentsByIds(List.of(instrumentId))).thenReturn(
                List.of(new InstrumentReference(instrumentId, "HOSE", symbol, "COMMON_EQUITY", "ACTIVE")));
    }

    private void givenListedInstrument(UUID id1, String symbol1, UUID id2, String symbol2) {
        EquityProfileEntity entity1 = profile(id1);
        EquityProfileEntity entity2 = profile(id2);
        when(profiles.findByEffectiveToIsNullAndListingStatus("LISTED")).thenReturn(List.of(entity1, entity2));
        when(referenceData.findInstrumentsByIds(List.of(id1, id2))).thenReturn(List.of(
                new InstrumentReference(id1, "HOSE", symbol1, "COMMON_EQUITY", "ACTIVE"),
                new InstrumentReference(id2, "HOSE", symbol2, "COMMON_EQUITY", "ACTIVE")));
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
