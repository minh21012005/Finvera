package com.minhnb.finvera_be.stock.provider.fixture;

import com.minhnb.finvera_be.stock.provider.StockQuoteProvider;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/** Deterministic development/test quote provider. Fixture names are compile-time allowlisted. */
public final class FixtureStockQuoteProvider implements StockQuoteProvider {

    private static final String FIXTURE_ROOT = "fixtures/stock/overview/";
    private static final Map<String, String> SYMBOL_TO_FIXTURE = Map.of(
            "FPT", "overview-complete.json");

    private final FixtureScenario scenario;

    public FixtureStockQuoteProvider(FixtureScenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
    }

    @Override
    public QuoteObservation getQuote(String symbol) {
        Objects.requireNonNull(symbol, "symbol");
        if (scenario == FixtureScenario.AUTH_REQUIRED) {
            throw new ProviderAuthenticationRequiredException();
        }
        String resourceName = scenario.resourceName() != null
                ? scenario.resourceName()
                : SYMBOL_TO_FIXTURE.get(symbol.toUpperCase());
        if (resourceName == null) {
            throw new IllegalArgumentException("No fixture is allowlisted for symbol: " + symbol);
        }
        JsonNode root = FixtureJson.read(FIXTURE_ROOT + resourceName);
        JsonNode price = root.path("price");
        return new QuoteObservation(
                FixtureJson.requiredText(root, "symbol"),
                FixtureJson.decimalOrNull(price, "last"),
                FixtureJson.decimalOrNull(price, "referencePrice"),
                FixtureJson.longOrNull(price, "volume"),
                FixtureJson.decimalOrNull(price, "valueVnd"),
                Instant.parse(FixtureJson.requiredText(root, "observedAt")),
                FixtureJson.requiredText(root, "sessionState"));
    }

    public enum FixtureScenario {
        COMPLETE("overview-complete.json"),
        DELAYED("overview-delayed.json"),
        STALE("overview-stale.json"),
        CLOSED_MARKET("overview-closed-market.json"),
        MISSING_REFERENCE("overview-missing-reference-price.json"),
        BY_SYMBOL(null),
        AUTH_REQUIRED(null);

        private final String resourceName;

        FixtureScenario(String resourceName) {
            this.resourceName = resourceName;
        }

        String resourceName() {
            return resourceName;
        }
    }
}
