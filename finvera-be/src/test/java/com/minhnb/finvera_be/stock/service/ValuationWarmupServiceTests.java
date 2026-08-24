package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValuationWarmupServiceTests {

    private final EquityProfileRepository equityProfiles = mock(EquityProfileRepository.class);
    private final MarketReferenceDataService referenceData = mock(MarketReferenceDataService.class);
    private final ValuationService valuations = mock(ValuationService.class);
    private final ValuationWarmupService warmup =
            new ValuationWarmupService(equityProfiles, referenceData, valuations);

    @Test
    void warmsEveryListedSymbolThroughTheCanonicalValuationService() {
        UUID fptId = UUID.randomUUID();
        UUID vnmId = UUID.randomUUID();
        when(equityProfiles.findByEffectiveToIsNullAndListingStatus("LISTED")).thenReturn(List.of(
                profile(fptId), profile(vnmId)));
        when(referenceData.findInstrumentsByIds(anyCollection())).thenReturn(List.of(
                new InstrumentReference(fptId, "HOSE", "FPT", "COMMON_STOCK", "ACTIVE"),
                new InstrumentReference(vnmId, "HOSE", "VNM", "COMMON_STOCK", "ACTIVE")));
        when(valuations.findBySymbol("FPT")).thenReturn(Optional.of(mock(ValuationService.StockValuation.class)));
        when(valuations.findBySymbol("VNM")).thenReturn(Optional.empty());

        var summary = warmup.warmUp();

        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.succeeded()).isEqualTo(1);
        assertThat(summary.unavailable()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        verify(valuations).findBySymbol("FPT");
        verify(valuations).findBySymbol("VNM");
    }

    private static EquityProfileEntity profile(UUID instrumentId) {
        return new EquityProfileEntity(UUID.randomUUID(), instrumentId, "Company", null,
                null, 1_000_000L, new BigDecimal("0.500000"), "LISTED",
                LocalDate.of(2024, 1, 1), null, "TEST", "1", null);
    }
}
