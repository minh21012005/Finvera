package com.minhnb.finvera_be.stock.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses an owner-supplied local canonical fundamentals package produced by
 * {@code tools/market-data/vnstock-export/export_fundamentals.py}. Mirrors {@code
 * MarketImportPackageParser}'s shape; it never calls a provider itself.
 */
@Component
public class FundamentalReportImportPackageParser {
    private final JsonMapper json = JsonMapper.builder().build();

    public FundamentalReportImportService.PackageInput parse(Path path) {
        try {
            JsonNode root = json.readTree(path.toFile());
            List<FundamentalReportImportService.MetricPeriodRecord> records = new ArrayList<>();
            for (JsonNode value : root.path("records")) {
                JsonNode quarterNode = value.path("fiscalQuarter");
                Integer fiscalQuarter = quarterNode.isNull() || quarterNode.isMissingNode()
                        ? null : quarterNode.intValue();
                records.add(new FundamentalReportImportService.MetricPeriodRecord(
                        text(value, "metricCode"), text(value, "periodType"), value.path("fiscalYear").intValue(),
                        fiscalQuarter, LocalDate.parse(text(value, "periodStart")),
                        LocalDate.parse(text(value, "periodEnd")), new BigDecimal(text(value, "value")),
                        text(value, "canonicalRecord")));
            }
            return new FundamentalReportImportService.PackageInput(text(root, "contractVersion"),
                    text(root, "toolName"), text(root, "toolVersion"), text(root, "upstreamSource"),
                    text(root, "symbol"), text(root, "reportKind"), text(root, "auditStatus"),
                    text(root, "currency"), root.path("unitScale").intValue(1), text(root, "packageSha256"),
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
}
