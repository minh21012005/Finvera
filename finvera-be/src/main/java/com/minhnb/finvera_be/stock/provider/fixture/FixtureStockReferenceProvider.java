package com.minhnb.finvera_be.stock.provider.fixture;

import com.minhnb.finvera_be.stock.provider.StockReferenceProvider;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * Deterministic development/test reference provider. Profile fields are
 * sourced from the same allowlisted overview fixtures the quote provider
 * uses, since both describe the same accepted {@code equity_profile}
 * snapshot. Sector classification is a small allowlisted constant, matching
 * the scope of the fixture-mode sector floor tests (gate G-04).
 */
public final class FixtureStockReferenceProvider implements StockReferenceProvider {

    private static final String FIXTURE_ROOT = "fixtures/stock/overview/";
    private static final Map<String, String> SYMBOL_TO_FIXTURE = Map.of(
            "FPT", "overview-complete.json");
    private static final String SCHEME = "finvera-sector-v1";
    private static final String SCHEME_VERSION = "2026-01";
    private static final List<SectorClassification> SECTORS = List.of(
            new SectorClassification(SCHEME, SCHEME_VERSION, "ICT", "Công nghệ thông tin", "Information Technology"));

    @Override
    public InstrumentReference findInstrument(String symbol) {
        Objects.requireNonNull(symbol, "symbol");
        String resourceName = SYMBOL_TO_FIXTURE.get(symbol.toUpperCase());
        if (resourceName == null) {
            throw new InstrumentNotFoundException(symbol);
        }
        JsonNode root = FixtureJson.read(FIXTURE_ROOT + resourceName);
        JsonNode profile = root.path("profile");
        return new InstrumentReference(
                FixtureJson.requiredText(root, "symbol"),
                "HOSE",
                FixtureJson.requiredText(profile, "companyName"),
                FixtureJson.textOrNull(profile, "companyNameEn"),
                FixtureJson.requiredText(profile, "listingStatus"),
                FixtureJson.longOrNull(profile, "sharesOutstanding"),
                "ICT",
                FixtureJson.textOrNull(profile, "sectorScheme"),
                SCHEME_VERSION,
                FixtureJson.requiredText(root, "source"),
                "fixture-v1");
    }

    @Override
    public List<SectorClassification> listSectorClassification() {
        return SECTORS;
    }
}
