package com.minhnb.finvera_be.market.service;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Parses an operator-supplied local canonical package; it does not call a provider. */
@Component
public class MarketImportPackageParser {
    private final JsonMapper json = JsonMapper.builder().build();

    public MarketImportService.PackageInput parse(Path path) {
        try {
            JsonNode root = json.readTree(path.toFile());
            List<MarketImportService.EquityHistoryRecord> records = new ArrayList<>();
            for (JsonNode value : root.path("records")) {
                records.add(new MarketImportService.EquityHistoryRecord(text(value, "venue"), text(value, "symbol"),
                        nullableText(value, "isin"), LocalDate.parse(text(value, "listedFrom")),
                        text(value, "instrumentStatus"), LocalDate.parse(text(value, "tradingDate")),
                        Instant.parse(text(value, "observedAt")), text(value, "closePrice"),
                        text(value, "adjustmentStatus"), nullableText(value, "sourceSequence"),
                        text(value, "canonicalRecord")));
            }
            return new MarketImportService.PackageInput(text(root, "contractVersion"), text(root, "toolName"),
                    text(root, "toolVersion"), text(root, "upstreamSource"), text(root, "packageSha256"),
                    text(root, "canonicalPayload"), Instant.parse(text(root, "generatedAt")),
                    LocalDate.parse(text(root, "rangeStart")), LocalDate.parse(text(root, "rangeEnd")), records);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("INVALID_IMPORT_PACKAGE", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.stringValue().isBlank()) throw new IllegalArgumentException("MISSING_" + field);
        return value.stringValue();
    }
    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.stringValue() : null;
    }
}
