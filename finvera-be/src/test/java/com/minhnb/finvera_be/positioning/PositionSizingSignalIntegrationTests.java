package com.minhnb.finvera_be.positioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.portfolio.service.PortfolioSizingDataService;
import com.minhnb.finvera_be.positioning.dto.PositionSizingDtos.*;
import com.minhnb.finvera_be.positioning.service.PositionSizingMetrics;
import com.minhnb.finvera_be.positioning.service.PositionSizingService;
import com.minhnb.finvera_be.stock.service.StockSizingDataService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PositionSizingSignalIntegrationTests {
    @Test void selectedMidpointAndStopAreResolvedFromTheCurrentServerSignal() {
        var fixture = fixture();
        given(fixture.stocks.resolveCurrentLongSignal("FPT", fixture.selector)).willReturn(Optional.of(
                new StockSizingDataService.Snapshot(UUID.randomUUID(), fixture.instrumentId, "FPT", "MOMENTUM",
                        "strategy-signal-v1", "LONG", new BigDecimal("49000"), new BigDecimal("51000"),
                        new BigDecimal("46000"), LocalDate.parse("2026-09-12"), fixture.calculatedAt, "signal:7")));

        var result = fixture.service.calculate(request(fixture, true));

        assertThat(result.resolvedEntryPriceVnd()).isEqualTo("50000");
        assertThat(result.resolvedStopPriceVnd()).isEqualTo("46000");
        assertThat(result.inputEvidence()).filteredOn(e -> e.source().equals("SIGNAL"))
                .allSatisfy(e -> assertThat(e.coherenceKey()).isEqualTo("signal:7"));
    }

    @Test void changedOrStaleSelectorWithholdsAndNeverFallsBackToCopiedPrices() {
        var fixture = fixture();
        given(fixture.stocks.resolveCurrentLongSignal("FPT", fixture.selector)).willReturn(Optional.empty());

        var result = fixture.service.calculate(request(fixture, true));

        assertThat(result.status()).isEqualTo("WITHHELD");
        assertThat(result.quantity()).isNull();
        assertThat(result.reasonCodes()).containsExactly("SIGNAL_NOT_CURRENT");
    }

    private static SizingRequest request(Fixture fixture, boolean confirmed) {
        return new SizingRequest(Mode.MANUAL, "FPT", null,
                new ManualCapital("100000000", "100000000", null, null, null),
                new RiskBudget(RiskKind.FIXED_VND, "1000000"),
                new PriceInput(PriceSource.SIGNAL, null, null, "MOMENTUM", "strategy-signal-v1",
                        fixture.calculatedAt, EntryBasis.MIDPOINT, confirmed),
                new CostPolicy(true, null, null, null, null, null), null);
    }

    private static Fixture fixture() {
        var market = mock(MarketReferenceDataService.class);
        var stocks = mock(StockSizingDataService.class);
        UUID instrumentId = UUID.randomUUID();
        Instant calculatedAt = Instant.parse("2026-09-13T07:00:00Z");
        var selector = new StockSizingDataService.Selector("MOMENTUM", "strategy-signal-v1", calculatedAt);
        given(market.findActiveInstrumentBySymbol("FPT")).willReturn(Optional.of(
                new MarketReferenceDataService.InstrumentReference(instrumentId, "HOSE", "FPT", "EQUITY", "ACTIVE")));
        return new Fixture(stocks, selector, instrumentId, calculatedAt,
                new PositionSizingService(market, mock(PortfolioSizingDataService.class), stocks,
                        new PositionSizingMetrics(new SimpleMeterRegistry()), Clock.systemUTC()));
    }

    private record Fixture(StockSizingDataService stocks, StockSizingDataService.Selector selector,
            UUID instrumentId, Instant calculatedAt, PositionSizingService service) { }
}
