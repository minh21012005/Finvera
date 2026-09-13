package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockSizingDataServiceTests {
    @Test void resolvesOnlyTheExactCurrentLongSelector() {
        var market = mock(MarketReferenceDataService.class);
        var stocks = mock(StockReferenceDataService.class);
        UUID instrumentId = UUID.randomUUID(); Instant at = Instant.parse("2026-09-12T08:00:00Z");
        given(market.findActiveInstrumentBySymbol("FPT")).willReturn(Optional.of(
                new MarketReferenceDataService.InstrumentReference(instrumentId, "HOSE", "FPT", "EQUITY", "ACTIVE")));
        given(stocks.findCurrentSignalsForInstruments(List.of(instrumentId))).willReturn(List.of(
                new StockReferenceDataService.SignalReference(UUID.randomUUID(), instrumentId, "MOMENTUM",
                        "strategy-signal-v1", LocalDate.parse("2026-09-11"), "LONG", new BigDecimal("49"),
                        new BigDecimal("51"), new BigDecimal("46"), null, null, null, null, null, at)));
        var service = new StockSizingDataService(market, stocks);
        assertThat(service.resolveCurrentLongSignal("FPT", new StockSizingDataService.Selector("MOMENTUM", "strategy-signal-v1", at))).isPresent();
        assertThat(service.resolveCurrentLongSignal("FPT", new StockSizingDataService.Selector("MOMENTUM", "old", at))).isEmpty();
    }
}
