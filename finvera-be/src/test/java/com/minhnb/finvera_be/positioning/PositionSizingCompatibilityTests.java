package com.minhnb.finvera_be.positioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.portfolio.service.PortfolioSizingDataService;
import com.minhnb.finvera_be.positioning.domain.PositionSizingV1;
import com.minhnb.finvera_be.positioning.dto.PositionSizingDtos.*;
import com.minhnb.finvera_be.positioning.service.PositionSizingMetrics;
import com.minhnb.finvera_be.positioning.service.PositionSizingService;
import com.minhnb.finvera_be.stock.service.StockSizingDataService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PositionSizingCompatibilityTests {
    @Test void publicOrchestrationAndDirectEngineReturnIdenticalFinancialFields() {
        var market = mock(MarketReferenceDataService.class);
        given(market.findActiveInstrumentBySymbol("FPT")).willReturn(Optional.of(
                new MarketReferenceDataService.InstrumentReference(UUID.randomUUID(), "HOSE", "FPT", "EQUITY", "ACTIVE")));
        var service = new PositionSizingService(market, mock(PortfolioSizingDataService.class),
                mock(StockSizingDataService.class), new PositionSizingMetrics(new SimpleMeterRegistry()), Clock.systemUTC());
        var request = new SizingRequest(Mode.MANUAL, "FPT", null,
                new ManualCapital("1000000", "1000000", null, null, null),
                new RiskBudget(RiskKind.FIXED_VND, "100000"),
                new PriceInput(PriceSource.MANUAL, "1000", "900", null, null, null, null, null),
                new CostPolicy(true, null, null, null, null, null), null);
        var publicResult = service.calculate(request);
        var direct = PositionSizingV1.calculate(new PositionSizingV1.Input(new BigDecimal("1000000"),
                new BigDecimal("1000000"), new BigDecimal("1000"), new BigDecimal("900"),
                new BigDecimal("100000"), PositionSizingV1.Costs.excluded(), null, null, null, null, null, 100));
        assertThat(publicResult.quantity()).isEqualTo(direct.quantity());
        assertThat(publicResult.rawPermittedQuantity()).isEqualTo(direct.rawPermittedQuantity());
        assertThat(publicResult.requiredCapitalVnd()).isEqualTo(direct.requiredCapitalVnd().stripTrailingZeros().toPlainString());
        assertThat(publicResult.estimatedLossAtStopVnd()).isEqualTo(direct.estimatedLossAtStopVnd().stripTrailingZeros().toPlainString());
        assertThat(publicResult.lossPerShareVnd()).isEqualTo(direct.lossPerShareVnd().stripTrailingZeros().toPlainString());
    }
}
