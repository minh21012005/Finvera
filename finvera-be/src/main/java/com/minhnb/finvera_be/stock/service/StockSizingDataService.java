package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Published stock-module boundary for resolving an explicitly selected current long signal. */
@Service
public class StockSizingDataService {
    private final MarketReferenceDataService market;
    private final StockReferenceDataService stocks;

    public StockSizingDataService(MarketReferenceDataService market, StockReferenceDataService stocks) {
        this.market = market;
        this.stocks = stocks;
    }

    public Optional<Snapshot> resolveCurrentLongSignal(String symbol, Selector selector) {
        var instrument = market.findActiveInstrumentBySymbol(symbol).orElse(null);
        if (instrument == null) return Optional.empty();
        List<StockReferenceDataService.SignalReference> signals = stocks.findCurrentSignalsForInstruments(
                List.of(instrument.instrumentId()));
        return signals.stream()
                .filter(s -> "LONG".equals(s.direction()))
                .filter(s -> s.strategyCode().equals(selector.strategyCode()))
                .filter(s -> s.ruleVersion().equals(selector.ruleVersion()))
                .filter(s -> s.calculatedAt().equals(selector.calculatedAt()))
                .findFirst()
                .map(s -> new Snapshot(s.id(), instrument.instrumentId(), symbol.toUpperCase(), s.strategyCode(),
                        s.ruleVersion(), s.direction(), s.entryLow(), s.entryHigh(), s.stopLoss(),
                        s.asOfTradingDate(), s.calculatedAt(), "signal:" + s.id()));
    }

    public record Selector(String strategyCode, String ruleVersion, Instant calculatedAt) { }
    public record Snapshot(UUID signalId, UUID instrumentId, String symbol, String strategyCode, String ruleVersion,
            String direction, java.math.BigDecimal entryLow, java.math.BigDecimal entryHigh,
            java.math.BigDecimal stopLoss, java.time.LocalDate tradingDate, Instant calculatedAt,
            String coherenceKey) { }
}
