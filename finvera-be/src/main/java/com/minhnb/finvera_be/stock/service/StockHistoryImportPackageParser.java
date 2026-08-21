package com.minhnb.finvera_be.stock.service;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses an owner-supplied local canonical daily-bar package produced by
 * {@code tools/market-data/vnstock-export/export_daily_bars.py}. Mirrors {@code
 * MarketImportPackageParser}'s shape; it never calls a provider itself.
 */
@Component
public class StockHistoryImportPackageParser {
    private final JsonMapper json = JsonMapper.builder().build();

    public StockHistoryImportService.PackageInput parse(Path path) {
        try {
            JsonNode root = json.readTree(path.toFile());
            List<StockHistoryImportService.DailyBarRecord> records = new ArrayList<>();
            for (JsonNode value : root.path("records")) {
                records.add(new StockHistoryImportService.DailyBarRecord(
                        text(value, "symbol"), LocalDate.parse(text(value, "tradingDate")),
                        Instant.parse(text(value, "observedAt")), text(value, "open"), text(value, "high"),
                        text(value, "low"), text(value, "close"), nullableText(value, "volume"),
                        nullableText(value, "valueVnd"), text(value, "adjustmentStatus"),
                        text(value, "canonicalRecord")));
            }
            return new StockHistoryImportService.PackageInput(text(root, "contractVersion"), text(root, "toolName"),
                    text(root, "toolVersion"), text(root, "upstreamSource"), text(root, "symbol"),
                    text(root, "packageSha256"), text(root, "canonicalPayload"),
                    Instant.parse(text(root, "generatedAt")), LocalDate.parse(text(root, "rangeStart")),
                    LocalDate.parse(text(root, "rangeEnd")), records);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("INVALID_IMPORT_PACKAGE", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.stringValue().isBlank()) {
            throw new IllegalArgumentException("MISSING_" + field);
        }
        return value.stringValue();
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.stringValue() : null;
    }
}
