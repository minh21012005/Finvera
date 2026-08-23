package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TechnicalIndicatorWarmupServiceTests {

    private final EquityProfileRepository profiles = mock(EquityProfileRepository.class);
    private final MarketReferenceDataService referenceData = mock(MarketReferenceDataService.class);
    private final TechnicalIndicatorService technicalIndicators = mock(TechnicalIndicatorService.class);
    private final TechnicalIndicatorWarmupService service =
            new TechnicalIndicatorWarmupService(profiles, referenceData, technicalIndicators);

    @Test
    void computesIndicatorsForEveryListedInstrumentBySymbol() {
        UUID vnmId = UUID.randomUUID();
        UUID fptId = UUID.randomUUID();
        EquityProfileEntity vnmProfile = profile(vnmId);
        EquityProfileEntity fptProfile = profile(fptId);
        when(profiles.findByEffectiveToIsNullAndListingStatus("LISTED")).thenReturn(List.of(vnmProfile, fptProfile));
        when(referenceData.findInstrumentsByIds(List.of(vnmId, fptId))).thenReturn(List.of(
                new InstrumentReference(vnmId, "HOSE", "VNM", "COMMON_EQUITY", "ACTIVE"),
                new InstrumentReference(fptId, "HOSE", "FPT", "COMMON_EQUITY", "ACTIVE")));

        var summary = service.warmUp();

        verify(technicalIndicators).findBySymbol("VNM");
        verify(technicalIndicators).findBySymbol("FPT");
        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.succeeded()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(0);
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

        verify(technicalIndicators, org.mockito.Mockito.never()).findBySymbol(any());
        assertThat(summary.failed()).isEqualTo(1);
    }

    private static EquityProfileEntity profile(UUID instrumentId) {
        EquityProfileEntity profile = mock(EquityProfileEntity.class);
        when(profile.getInstrumentId()).thenReturn(instrumentId);
        return profile;
    }
}
