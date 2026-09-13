package com.minhnb.finvera_be.positioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.portfolio.service.PortfolioSizingDataService;
import com.minhnb.finvera_be.positioning.dto.PositionSizingDtos.*;
import com.minhnb.finvera_be.positioning.service.PositionSizingExceptions.InvalidSizingRequestException;
import com.minhnb.finvera_be.positioning.service.PositionSizingMetrics;
import com.minhnb.finvera_be.positioning.service.PositionSizingService;
import com.minhnb.finvera_be.stock.service.StockSizingDataService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PositionSizingServiceTests {
    private final MarketReferenceDataService market = mock(MarketReferenceDataService.class);
    private final PortfolioSizingDataService portfolios = mock(PortfolioSizingDataService.class);
    private final StockSizingDataService stocks = mock(StockSizingDataService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final UUID instrumentId = UUID.randomUUID();
    private PositionSizingService service;

    @BeforeEach
    void setUp() {
        service = new PositionSizingService(market, portfolios, stocks, new PositionSizingMetrics(registry),
                Clock.fixed(Instant.parse("2026-09-12T08:00:00Z"), ZoneOffset.UTC));
        given(market.findActiveInstrumentBySymbol("FPT")).willReturn(Optional.of(
                new MarketReferenceDataService.InstrumentReference(instrumentId, "HOSE", "FPT", "EQUITY", "ACTIVE")));
    }

    @Test
    void manualFixedRiskReturnsAuditableDeterministicResult() {
        SizingRequest request = manual(new RiskBudget(RiskKind.FIXED_VND, "100000"));
        var first = service.calculate(request);
        var second = service.calculate(request);
        assertThat(first.status()).isEqualTo("CALCULATED");
        assertThat(first.quantity()).isEqualTo(1000L);
        assertThat(first).usingRecursiveComparison().isEqualTo(second);
        assertThat(first.constraints()).hasSize(4);
        assertThat(first.constraints()).filteredOn(x -> x.applicability().equals("NOT_APPLIED")).hasSize(2);
        assertThat(first.inputEvidence()).extracting(InputEvidence::source).contains("OWNER_ENTERED", "MARKET_RULE");
        assertThat(first.inputEvidence()).filteredOn(e -> e.source().equals("MARKET_RULE"))
                .allSatisfy(e -> assertThat(e.asOf()).isNotNull());
        assertThat(first.inputEvidence()).extracting(InputEvidence::field)
                .contains("resolvedSymbol", "venue", "instrumentStatus", "marketRuleSource");
    }

    @Test void declaredAllZeroCostsCannotBypassTheExplicitExclusionDisclosure() {
        var request = new SizingRequest(Mode.MANUAL, "FPT", null,
                new ManualCapital("1000000", "1000000", null, null, null),
                new RiskBudget(RiskKind.FIXED_VND, "10000"),
                new PriceInput(PriceSource.MANUAL, "1000", "900", null, null, null, null, null),
                new CostPolicy(false, "0", "0", "0", "0", "0"), null);

        assertThatThrownBy(() -> service.calculate(request)).isInstanceOf(InvalidSizingRequestException.class)
                .satisfies(error -> assertThat(((InvalidSizingRequestException) error).reasonCode())
                        .isEqualTo("INCOMPLETE_COST_POLICY"));
    }

    @Test
    void percentRiskUsesCapitalBaseAndExplicitCostExclusionIsDisclosed() {
        var result = service.calculate(manual(new RiskBudget(RiskKind.PERCENT, "0.01")));
        assertThat(result.riskBudgetVnd()).isEqualTo("10000");
        assertThat(result.warnings()).contains("COSTS_EXCLUDED", "ENTRY_FEE_EXCLUDED", "SELL_TAX_EXCLUDED");
        assertThat(result.costsExcluded()).isTrue();
    }

    @Test
    void rejectsPartialCostsAndCrossModeInputs() {
        var partialCosts = new SizingRequest(Mode.MANUAL, "FPT", null,
                new ManualCapital("1000000", "1000000", null, null, null),
                new RiskBudget(RiskKind.FIXED_VND, "10000"),
                new PriceInput(PriceSource.MANUAL, "1000", "900", null, null, null, null, null),
                new CostPolicy(false, "0.001", null, null, null, null), null);
        assertThatThrownBy(() -> service.calculate(partialCosts))
                .isInstanceOf(InvalidSizingRequestException.class);
        var badMode = new SizingRequest(Mode.MANUAL, "FPT", UUID.randomUUID(), partialCosts.manualCapital(),
                partialCosts.riskBudget(), partialCosts.priceInput(), new CostPolicy(true, null, null, null, null, null), null);
        assertThatThrownBy(() -> service.calculate(badMode)).isInstanceOf(InvalidSizingRequestException.class);
    }

    @Test
    void unsupportedSymbolWithholdsInsteadOfInventingRule() {
        var request = new SizingRequest(Mode.MANUAL, "ZZZ", null,
                new ManualCapital("1000000", "1000000", null, null, null),
                new RiskBudget(RiskKind.FIXED_VND, "10000"),
                new PriceInput(PriceSource.MANUAL, "1000", "900", null, null, null, null, null),
                new CostPolicy(true, null, null, null, null, null), null);
        assertThat(service.calculate(request).reasonCodes()).containsExactly("MARKET_LOT_RULE_UNAVAILABLE");
    }

    @Test
    void malformedShapeIsRejectedBeforeUnsupportedSymbolLookupCanWithhold() {
        var request = new SizingRequest(Mode.MANUAL, "ZZZ", UUID.randomUUID(),
                new ManualCapital("1000000", "1000000", null, null, null),
                new RiskBudget(RiskKind.FIXED_VND, "10000"),
                new PriceInput(PriceSource.MANUAL, "1000", "900", null, null, null, null, null),
                new CostPolicy(true, null, null, null, null, null), null);
        assertThatThrownBy(() -> service.calculate(request)).isInstanceOf(InvalidSizingRequestException.class)
                .satisfies(error -> assertThat(((InvalidSizingRequestException) error).reasonCode())
                        .isEqualTo("INVALID_INPUT"));
    }

    @Test
    void portfolioModeUsesSnapshotTotalAsPercentRiskBaseAndCashAsSeparateCap() {
        UUID id = UUID.randomUUID();
        given(portfolios.resolve(id, "FPT")).willReturn(new PortfolioSizingDataService.Snapshot(id,
                new java.math.BigDecimal("30000000"), new java.math.BigDecimal("100000000"),
                new java.math.BigDecimal("70000000"), new java.math.BigDecimal("500"),
                new java.math.BigDecimal("25000000"), "CURRENT", List.of(), "portfolio-coherence",
                Instant.parse("2026-09-12T07:59:00Z")));
        var request = new SizingRequest(Mode.PORTFOLIO, "FPT", id, null,
                new RiskBudget(RiskKind.PERCENT, "0.01"),
                new PriceInput(PriceSource.MANUAL, "50000", "47000", null, null, null, null, null),
                new CostPolicy(true, null, null, null, null, null), new ExposureLimits("0.30", "0.90"));
        var result = service.calculate(request);
        assertThat(result.capitalBaseVnd()).isEqualTo("100000000");
        assertThat(result.availableCashVnd()).isEqualTo("30000000");
        assertThat(result.riskBudgetVnd()).isEqualTo("1000000");
        assertThat(result.inputEvidence()).filteredOn(e -> e.source().equals("PORTFOLIO"))
                .allSatisfy(e -> assertThat(e.coherenceKey()).isEqualTo("portfolio-coherence"));
    }

    @Test
    void signalSelectorIsServerResolvedAndMustBeConfirmed() {
        Instant calculatedAt = Instant.parse("2026-09-12T07:00:00Z");
        var selector = new StockSizingDataService.Selector("MOMENTUM", "strategy-signal-v1", calculatedAt);
        given(stocks.resolveCurrentLongSignal("FPT", selector)).willReturn(Optional.of(
                new StockSizingDataService.Snapshot(UUID.randomUUID(), instrumentId, "FPT", "MOMENTUM",
                        "strategy-signal-v1", "LONG", new java.math.BigDecimal("49000"),
                        new java.math.BigDecimal("51000"), new java.math.BigDecimal("46000"),
                        java.time.LocalDate.parse("2026-09-11"), calculatedAt, "signal-coherence")));
        var request = new SizingRequest(Mode.MANUAL, "FPT", null,
                new ManualCapital("100000000", "100000000", null, null, null),
                new RiskBudget(RiskKind.FIXED_VND, "1000000"),
                new PriceInput(PriceSource.SIGNAL, null, null, "MOMENTUM", "strategy-signal-v1",
                        calculatedAt, EntryBasis.MIDPOINT, true),
                new CostPolicy(true, null, null, null, null, null), null);
        var result = service.calculate(request);
        assertThat(result.resolvedEntryPriceVnd()).isEqualTo("50000");
        assertThat(result.resolvedStopPriceVnd()).isEqualTo("46000");
        assertThat(result.inputEvidence()).extracting(InputEvidence::source).contains("SIGNAL");
    }

    @Test void manualOverrideKeepsOriginatingSignalOnlyAsNonAuthoritativeContext() {
        var context = new OriginatingSignalContext("MOMENTUM", "strategy-signal-v1",
                Instant.parse("2026-09-12T07:00:00Z"), java.time.LocalDate.parse("2026-09-11"));
        var request = new SizingRequest(Mode.MANUAL, "FPT", null,
                new ManualCapital("1000000", "1000000", null, null, null),
                new RiskBudget(RiskKind.FIXED_VND, "10000"),
                new PriceInput(PriceSource.MANUAL, "1000", "900", null, null, null, null, null, context),
                new CostPolicy(true, null, null, null, null, null), null);

        var result = service.calculate(request);

        assertThat(result.resolvedEntryPriceVnd()).isEqualTo("1000");
        assertThat(result.inputEvidence()).filteredOn(e -> e.source().equals("SIGNAL_CONTEXT"))
                .extracting(InputEvidence::field)
                .containsExactly("originatingSignalStrategy", "originatingSignalRuleVersion",
                        "originatingSignalTradingDate");
    }

    private static SizingRequest manual(RiskBudget risk) {
        return new SizingRequest(Mode.MANUAL, "FPT", null,
                new ManualCapital("1000000", "1000000", null, null, null), risk,
                new PriceInput(PriceSource.MANUAL, "1000", "900", null, null, null, null, null),
                new CostPolicy(true, null, null, null, null, null), null);
    }
}
