package com.minhnb.finvera_be.stock.provider.fixture;

import com.minhnb.finvera_be.stock.provider.FundamentalReportProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * Deterministic development/test fundamental-report provider. Gate G-01 is
 * open, so this fixture stands in only for domain/persistence/contract
 * testing; it is never presented as live provider evidence and its
 * {@code sourceLineItem} values are the Finvera metric codes themselves
 * (an identity mapping), not a real captured provider field name.
 */
public final class FixtureFundamentalReportProvider implements FundamentalReportProvider {

    private static final String FIXTURE_ROOT = "fixtures/stock/fundamentals/";

    private final FixtureScenario scenario;

    public FixtureFundamentalReportProvider(FixtureScenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
    }

    @Override
    public List<FundamentalReport> getReports(
            String symbol, String periodType, LocalDate fromPeriod, LocalDate toPeriod) {
        Objects.requireNonNull(symbol, "symbol");
        JsonNode root = FixtureJson.read(FIXTURE_ROOT + scenario.resourceName());
        if (root.has("revisions")) {
            return FixtureJson.stream(root.path("revisions")).map(FixtureFundamentalReportProvider::toReport)
                    .filter(report -> withinRange(report, fromPeriod, toPeriod))
                    .toList();
        }
        FundamentalReport report = toReport(root);
        return withinRange(report, fromPeriod, toPeriod) ? List.of(report) : List.of();
    }

    private static boolean withinRange(FundamentalReport report, LocalDate fromPeriod, LocalDate toPeriod) {
        if (fromPeriod != null && report.periodEnd().isBefore(fromPeriod)) {
            return false;
        }
        return toPeriod == null || !report.periodEnd().isAfter(toPeriod);
    }

    private static FundamentalReport toReport(JsonNode revisionOrRoot) {
        JsonNode report = revisionOrRoot.path("report");
        List<LineItem> lineItems = FixtureJson.stream(revisionOrRoot.path("metrics"))
                .map(metric -> new LineItem(
                        FixtureJson.requiredText(metric, "metricCode"),
                        FixtureJson.decimalOrNull(metric, "value")))
                .toList();
        return new FundamentalReport(
                FixtureJson.requiredText(report, "periodType"),
                FixtureJson.intOrNull(report, "fiscalYear"),
                FixtureJson.intOrNull(report, "fiscalQuarter"),
                LocalDate.parse(FixtureJson.requiredText(report, "periodStart")),
                LocalDate.parse(FixtureJson.requiredText(report, "periodEnd")),
                FixtureJson.requiredText(report, "reportKind"),
                FixtureJson.requiredText(report, "auditStatus"),
                FixtureJson.requiredText(report, "currency"),
                FixtureJson.intOrNull(report, "unitScale"),
                lineItems);
    }

    public enum FixtureScenario {
        COMPLETE("fundamentals-complete.json"),
        RESTATED("fundamentals-restated.json"),
        NEGATIVE_EARNINGS("fundamentals-negative-earnings.json"),
        STALE("fundamentals-stale-300d.json");

        private final String resourceName;

        FixtureScenario(String resourceName) {
            this.resourceName = resourceName;
        }

        String resourceName() {
            return resourceName;
        }
    }
}
