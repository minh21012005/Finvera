package com.minhnb.finvera_be.stock.provider.fixture;

import com.minhnb.finvera_be.stock.provider.CorporateActionProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/** Deterministic development/test corporate-action provider. Fixture names are compile-time allowlisted. */
public final class FixtureCorporateActionProvider implements CorporateActionProvider {

    private static final String SPLIT_FIXTURE = "fixtures/stock/chart/chart-split-in-window.json";

    private final FixtureScenario scenario;

    public FixtureCorporateActionProvider(FixtureScenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
    }

    @Override
    public List<CorporateAction> getCorporateActions(String symbol, LocalDate fromDate, LocalDate toDate) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(fromDate, "fromDate");
        Objects.requireNonNull(toDate, "toDate");
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate must not precede fromDate");
        }
        if (scenario == FixtureScenario.NONE) {
            return List.of();
        }
        JsonNode action = FixtureJson.read(SPLIT_FIXTURE).path("corporateAction");
        LocalDate exDate = LocalDate.parse(FixtureJson.requiredText(action, "exDate"));
        if (exDate.isBefore(fromDate) || exDate.isAfter(toDate)) {
            return List.of();
        }
        return List.of(new CorporateAction(
                FixtureJson.requiredText(action, "actionType"),
                exDate,
                null,
                null,
                FixtureJson.decimalOrNull(action, "ratioNumerator"),
                FixtureJson.decimalOrNull(action, "ratioDenominator"),
                null,
                FixtureJson.requiredText(action, "source")));
    }

    public enum FixtureScenario {
        SPLIT_IN_WINDOW, NONE
    }
}
