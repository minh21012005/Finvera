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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PositionSizingPortfolioIntegrationTests {
    @Test void coherentPortfolioValuesApplyCashAndExposureCaps() {
        var fixture = fixture();
        UUID portfolioId = UUID.randomUUID();
        given(fixture.portfolios.resolve(portfolioId, "FPT")).willReturn(new PortfolioSizingDataService.Snapshot(
                portfolioId, new BigDecimal("30000000"), new BigDecimal("100000000"),
                new BigDecimal("70000000"), new BigDecimal("500"), new BigDecimal("25000000"),
                "CURRENT", List.of(), "portfolio:version-7", Instant.parse("2026-09-13T07:00:00Z")));

        var result = fixture.service.calculate(request(portfolioId));

        assertThat(result.status()).isEqualTo("CALCULATED");
        assertThat(result.constraints()).filteredOn(c -> c.code().equals("SYMBOL_CONCENTRATION"))
                .singleElement().satisfies(c -> assertThat(c.applicability()).isEqualTo("APPLIED"));
        assertThat(result.inputEvidence()).filteredOn(e -> e.source().equals("PORTFOLIO"))
                .allSatisfy(e -> assertThat(e.coherenceKey()).isEqualTo("portfolio:version-7"));
    }

    @Test void unavailablePortfolioFactsNeverProduceAQuantity() {
        var fixture = fixture();
        UUID portfolioId = UUID.randomUUID();
        given(fixture.portfolios.resolve(portfolioId, "FPT")).willReturn(new PortfolioSizingDataService.Snapshot(
                portfolioId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                "UNAVAILABLE", List.of("MISSING_PRICE"), "portfolio:version-8", Instant.parse("2026-09-13T07:00:00Z")));

        var result = fixture.service.calculate(request(portfolioId));

        assertThat(result.status()).isEqualTo("WITHHELD");
        assertThat(result.quantity()).isNull();
        assertThat(result.reasonCodes()).containsExactly("PORTFOLIO_DATA_UNAVAILABLE");
    }

    @Test void currentLabelWithoutCoherenceOrAcceptedTimeIsStillWithheld() {
        var fixture = fixture();
        UUID portfolioId = UUID.randomUUID();
        given(fixture.portfolios.resolve(portfolioId, "FPT")).willReturn(new PortfolioSizingDataService.Snapshot(
                portfolioId, new BigDecimal("30000000"), new BigDecimal("100000000"),
                new BigDecimal("70000000"), BigDecimal.ZERO, BigDecimal.ZERO,
                "CURRENT", List.of(), null, null));

        var result = fixture.service.calculate(request(portfolioId));

        assertThat(result.status()).isEqualTo("WITHHELD");
        assertThat(result.reasonCodes()).containsExactly("PORTFOLIO_DATA_UNAVAILABLE");
    }

    @Test void inconsistentNegativeDeployedValueIsWithheldAsUnavailablePortfolioData() {
        var fixture = fixture();
        UUID portfolioId = UUID.randomUUID();
        given(fixture.portfolios.resolve(portfolioId, "FPT")).willReturn(new PortfolioSizingDataService.Snapshot(
                portfolioId, new BigDecimal("110000000"), new BigDecimal("100000000"),
                new BigDecimal("-10000000"), BigDecimal.ZERO, BigDecimal.ZERO,
                "CURRENT", List.of(), "portfolio:bad", Instant.parse("2026-09-13T07:00:00Z")));

        var result = fixture.service.calculate(request(portfolioId));

        assertThat(result.status()).isEqualTo("WITHHELD");
        assertThat(result.reasonCodes()).containsExactly("PORTFOLIO_DATA_UNAVAILABLE");
    }

    private static SizingRequest request(UUID id) {
        return new SizingRequest(Mode.PORTFOLIO, "FPT", id, null,
                new RiskBudget(RiskKind.PERCENT, "0.01"),
                new PriceInput(PriceSource.MANUAL, "50000", "47000", null, null, null, null, null),
                new CostPolicy(true, null, null, null, null, null), new ExposureLimits("0.30", "0.90"));
    }

    private static Fixture fixture() {
        var market = mock(MarketReferenceDataService.class);
        var portfolios = mock(PortfolioSizingDataService.class);
        given(market.findActiveInstrumentBySymbol("FPT")).willReturn(Optional.of(
                new MarketReferenceDataService.InstrumentReference(UUID.randomUUID(), "HOSE", "FPT", "EQUITY", "ACTIVE")));
        return new Fixture(portfolios, new PositionSizingService(market, portfolios,
                mock(StockSizingDataService.class), new PositionSizingMetrics(new SimpleMeterRegistry()), Clock.systemUTC()));
    }

    private record Fixture(PortfolioSizingDataService portfolios, PositionSizingService service) { }
}
