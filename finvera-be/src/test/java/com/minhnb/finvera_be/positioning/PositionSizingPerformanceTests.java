package com.minhnb.finvera_be.positioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.positioning.domain.PositionSizingV1;
import com.minhnb.finvera_be.positioning.service.PositionSizingMetrics;
import com.minhnb.finvera_be.positioning.service.PositionSizingService;
import com.minhnb.finvera_be.positioning.dto.PositionSizingDtos.*;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.portfolio.service.PortfolioSizingDataService;
import com.minhnb.finvera_be.stock.service.StockSizingDataService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.Test;

class PositionSizingPerformanceTests {
    @Test void warmedPureCalculationsMeetOneSecondP95ByWideMargin() {
        var input = new PositionSizingV1.Input(new BigDecimal("500000000"), new BigDecimal("300000000"),
                new BigDecimal("50000"), new BigDecimal("47000"), new BigDecimal("10000000"),
                PositionSizingV1.Costs.excluded(), null, null, null, null, null, 100);
        for (int i = 0; i < 100; i++) PositionSizingV1.calculate(input);
        long[] samples = new long[1000];
        for (int i = 0; i < samples.length; i++) {
            long start = System.nanoTime(); PositionSizingV1.calculate(input); samples[i] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        assertThat(samples[949]).isLessThan(1_000_000_000L);
    }

    @Test void metricsUseOnlyBoundedNonFinancialTags() {
        var registry = new SimpleMeterRegistry();
        var metrics = new PositionSizingMetrics(registry);
        var sample = metrics.start();
        metrics.finish(sample, "MANUAL", "WITHHELD", "BELOW_STANDARD_LOT");
        assertThat(registry.getMeters()).allSatisfy(meter -> meter.getId().getTags().forEach(tag -> {
            assertThat(tag.getKey()).isIn("mode", "outcome", "reason", "version");
            assertThat(tag.getValue()).doesNotContain("50000", "FPT", "00000000-");
        }));
    }

    @Test void warmedPortfolioOrchestrationMeetsTwoSecondP95() {
        var market = mock(MarketReferenceDataService.class);
        var portfolios = mock(PortfolioSizingDataService.class);
        UUID id = UUID.randomUUID();
        given(market.findActiveInstrumentBySymbol("FPT")).willReturn(Optional.of(
                new MarketReferenceDataService.InstrumentReference(UUID.randomUUID(), "HOSE", "FPT", "EQUITY", "ACTIVE")));
        given(portfolios.resolve(id, "FPT")).willReturn(new PortfolioSizingDataService.Snapshot(id,
                new BigDecimal("30000000"), new BigDecimal("100000000"), new BigDecimal("70000000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "CURRENT", List.of(), "portfolio:1", Instant.EPOCH));
        var service = new PositionSizingService(market, portfolios, mock(StockSizingDataService.class),
                new PositionSizingMetrics(new SimpleMeterRegistry()), Clock.systemUTC());
        var request = new SizingRequest(Mode.PORTFOLIO, "FPT", id, null,
                new RiskBudget(RiskKind.PERCENT, "0.01"),
                new PriceInput(PriceSource.MANUAL, "50000", "47000", null, null, null, null, null),
                new CostPolicy(true, null, null, null, null, null), null);
        for (int i = 0; i < 100; i++) service.calculate(request);
        long[] samples = new long[100];
        for (int i = 0; i < samples.length; i++) {
            long start = System.nanoTime(); service.calculate(request); samples[i] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        assertThat(samples[94]).isLessThan(2_000_000_000L);
    }
}
