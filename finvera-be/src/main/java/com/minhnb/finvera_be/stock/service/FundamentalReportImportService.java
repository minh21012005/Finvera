package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalReportAcceptance;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingFundamentalReport;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionResult;
import com.minhnb.finvera_be.stock.service.StockIngestionService.MetricValue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Owner-operated, offline import boundary for fundamental-report metrics produced by
 * {@code tools/market-data/vnstock-export/export_fundamentals.py} (ADR-0004; research.md R-012
 * gate G-01, owner-accepted narrower scope). Groups the package's flat metric-period records back
 * into one {@link IncomingFundamentalReport} per fiscal period and delegates all persistence,
 * dedup, restatement, and metric-allowlist logic to the already-built {@link
 * StockIngestionService#ingestFundamentalReport(IncomingFundamentalReport)} — this class only
 * validates the package envelope and reshapes records into that call.
 */
@Service
@ConditionalOnProperty(name = "finvera.stock.import.fundamentals.enabled", havingValue = "true")
public class FundamentalReportImportService {

    static final String CONTRACT_VERSION = "vnstock-fundamentals-v1";
    private static final String SOURCE_PREFIX = "VNSTOCK";

    private final StockIngestionService ingestion;

    public FundamentalReportImportService(StockIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    public Summary importPackage(PackageInput input) {
        validate(input);
        Map<PeriodKey, List<MetricPeriodRecord>> byPeriod = new LinkedHashMap<>();
        for (MetricPeriodRecord record : input.records()) {
            byPeriod.computeIfAbsent(
                    new PeriodKey(record.periodType(), record.fiscalYear(), record.fiscalQuarter()),
                    key -> new ArrayList<>()).add(record);
        }

        List<IngestionResult> results = new ArrayList<>(byPeriod.size());
        for (var entry : byPeriod.entrySet()) {
            PeriodKey period = entry.getKey();
            List<MetricPeriodRecord> metricRecords = entry.getValue();
            List<MetricValue> metrics = metricRecords.stream()
                    .filter(r -> FundamentalReportAcceptance.ALLOWED_METRIC_CODES.contains(r.metricCode()))
                    .map(r -> new MetricValue(r.metricCode(), r.value(), MetricApplicability.DEFINED.name(), null))
                    .toList();
            LocalDate periodStart = metricRecords.get(0).periodStart();
            LocalDate periodEnd = metricRecords.get(0).periodEnd();
            results.add(ingestion.ingestFundamentalReport(new IncomingFundamentalReport(
                    input.upstreamSource(), input.symbol(), period.periodType(), period.fiscalYear(),
                    period.fiscalQuarter(), periodStart, periodEnd, input.reportKind(), input.auditStatus(),
                    input.currency(), input.unitScale(), FundamentalReportAcceptance.CATALOG_VERSION_V1,
                    input.generatedAt(), metrics, false, null)));
        }
        return new Summary(input.symbol(), results);
    }

    private static void validate(PackageInput input) {
        if (input == null || !CONTRACT_VERSION.equals(input.contractVersion())) {
            fail("UNSUPPORTED_CONTRACT");
        }
        required(input.toolName(), "INVALID_TOOL");
        required(input.toolVersion(), "INVALID_TOOL");
        required(input.upstreamSource(), "INVALID_SOURCE");
        if (!input.upstreamSource().startsWith(SOURCE_PREFIX)) {
            fail("INVALID_SOURCE");
        }
        if (input.symbol() == null || !input.symbol().matches("[A-Z0-9]{1,32}")) {
            fail("INVALID_SYMBOL");
        }
        if (input.unitScale() <= 0) {
            fail("INVALID_UNIT_SCALE");
        }
        if (input.generatedAt() == null) {
            fail("INVALID_RANGE");
        }
        if (input.records() == null || input.records().isEmpty()) {
            fail("EMPTY_PACKAGE");
        }
        if (input.canonicalPayload() == null || input.canonicalPayload().isBlank()
                || input.packageSha256() == null || !input.packageSha256().matches("[0-9a-f]{64}")
                || !input.packageSha256().equals(sha256(input.canonicalPayload()))) {
            fail("INVALID_CHECKSUM");
        }
        for (MetricPeriodRecord record : input.records()) {
            if (record.periodEnd().isBefore(record.periodStart())) {
                fail("INVALID_PERIOD");
            }
            required(record.canonicalRecord(), "INVALID_RECORD");
        }
    }

    private static void required(String value, String code) {
        if (value == null || value.isBlank()) {
            fail(code);
        }
    }

    private static void fail(String code) {
        throw new IllegalArgumentException(code);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record PeriodKey(String periodType, int fiscalYear, Integer fiscalQuarter) {
    }

    public record PackageInput(
            String contractVersion, String toolName, String toolVersion, String upstreamSource, String symbol,
            String reportKind, String auditStatus, String currency, int unitScale, String packageSha256,
            String canonicalPayload, Instant generatedAt, List<MetricPeriodRecord> records) {
    }

    public record MetricPeriodRecord(
            String metricCode, String periodType, int fiscalYear, Integer fiscalQuarter, LocalDate periodStart,
            LocalDate periodEnd, java.math.BigDecimal value, String canonicalRecord) {
    }

    public record Summary(String symbol, List<IngestionResult> results) {
    }
}
