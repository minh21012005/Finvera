package com.minhnb.finvera_be.stock.provider.fixture;

import com.minhnb.finvera_be.stock.provider.StockHistoryProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/** Deterministic development/test daily-bar provider. Fixture names are compile-time allowlisted. */
public final class FixtureStockHistoryProvider implements StockHistoryProvider {

    private final String classpathLocation;

    public FixtureStockHistoryProvider(FixtureScenario scenario) {
        this.classpathLocation = Objects.requireNonNull(scenario, "scenario").classpathLocation();
    }

    @Override
    public List<DailyBar> getDailyBars(String symbol, LocalDate fromDate, LocalDate toDate) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(fromDate, "fromDate");
        Objects.requireNonNull(toDate, "toDate");
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate must not precede fromDate");
        }
        JsonNode root = FixtureJson.read(classpathLocation);
        String adjustmentIndication = FixtureJson.requiredText(root, "adjustmentStatus");
        return FixtureJson.stream(root.path("bars"))
                .map(bar -> toDailyBar(bar, adjustmentIndication))
                .filter(bar -> !bar.tradingDate().isBefore(fromDate) && !bar.tradingDate().isAfter(toDate))
                .toList();
    }

    private static DailyBar toDailyBar(JsonNode bar, String adjustmentIndication) {
        return new DailyBar(
                LocalDate.parse(FixtureJson.requiredText(bar, "tradingDate")),
                FixtureJson.decimalOrNull(bar, "open"),
                FixtureJson.decimalOrNull(bar, "high"),
                FixtureJson.decimalOrNull(bar, "low"),
                FixtureJson.decimalOrNull(bar, "close"),
                FixtureJson.longOrNull(bar, "volume"),
                FixtureJson.decimalOrNull(bar, "valueVnd"),
                adjustmentIndication);
    }

    public enum FixtureScenario {
        GOLDEN_250("fixtures/stock/technical/bars-golden-250.json"),
        FLAT("fixtures/stock/technical/bars-flat.json"),
        MONOTONIC_RISING("fixtures/stock/technical/bars-monotonic-rising.json"),
        BARS_19("fixtures/stock/technical/bars-19.json"),
        BARS_20("fixtures/stock/technical/bars-20.json"),
        BARS_21("fixtures/stock/technical/bars-21.json"),
        BARS_49("fixtures/stock/technical/bars-49.json"),
        BARS_50("fixtures/stock/technical/bars-50.json"),
        BARS_199("fixtures/stock/technical/bars-199.json"),
        BARS_200("fixtures/stock/technical/bars-200.json"),
        BARS_249("fixtures/stock/technical/bars-249.json"),
        BARS_250("fixtures/stock/technical/bars-250.json"),
        CHART_COMPLETE("fixtures/stock/chart/chart-complete.json"),
        CHART_ADJUSTMENT_BASIS_UNAVAILABLE("fixtures/stock/chart/chart-adjustment-basis-unavailable.json");

        private final String classpathLocation;

        FixtureScenario(String classpathLocation) {
            this.classpathLocation = classpathLocation;
        }

        String classpathLocation() {
            return classpathLocation;
        }
    }
}
