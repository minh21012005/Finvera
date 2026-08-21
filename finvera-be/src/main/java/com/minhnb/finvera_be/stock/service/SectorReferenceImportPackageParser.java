package com.minhnb.finvera_be.stock.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses an owner-supplied local canonical sector-classification package produced by
 * {@code tools/market-data/vnstock-export/export_sector_reference.py}. Mirrors {@code
 * MarketImportPackageParser}'s shape; it never calls a provider itself.
 */
@Component
public class SectorReferenceImportPackageParser {
    private final JsonMapper json = JsonMapper.builder().build();

    public SectorReferenceImportService.PackageInput parse(Path path) {
        try {
            JsonNode root = json.readTree(path.toFile());
            List<SectorReferenceImportService.ClassificationRecord> records = new ArrayList<>();
            for (JsonNode value : root.path("records")) {
                records.add(new SectorReferenceImportService.ClassificationRecord(
                        text(value, "symbol"), text(value, "sectorCode"), text(value, "displayNameVi"),
                        nullableText(value, "displayNameEn"), text(value, "canonicalRecord")));
            }
            return new SectorReferenceImportService.PackageInput(text(root, "contractVersion"),
                    text(root, "toolName"), text(root, "toolVersion"), text(root, "upstreamSource"),
                    text(root, "scheme"), text(root, "schemeVersion"), text(root, "packageSha256"),
                    text(root, "canonicalPayload"), Instant.parse(text(root, "generatedAt")), records);
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
