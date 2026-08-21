package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Owner-operated, offline import boundary for full-OHLCV daily bars produced by
 * {@code tools/market-data/vnstock-export/export_daily_bars.py} (ADR-0004: Vnstock never runs
 * live/scheduled/per-request; only this bounded local-package path). Package-level structure is
 * validated here; per-record persistence, dedup, and OHLC-bounds checks are delegated entirely to
 * the already-built {@link StockIngestionService#ingestDailyBar(IncomingDailyBar)} so this class
 * does not duplicate that logic.
 */
@Service
@ConditionalOnProperty(name = "finvera.stock.import.daily-bar.enabled", havingValue = "true")
public class StockHistoryImportService {

    static final String CONTRACT_VERSION = "vnstock-daily-bar-v1";
    private static final String SOURCE_PREFIX = "VNSTOCK";

    private final StockIngestionService ingestion;

    public StockHistoryImportService(StockIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    public Summary importPackage(PackageInput input) {
        validate(input);
        List<IngestionResult> results = new ArrayList<>(input.records().size());
        for (DailyBarRecord record : input.records()) {
            results.add(ingestion.ingestDailyBar(new IncomingDailyBar(
                    input.upstreamSource(), record.symbol(), record.tradingDate(), record.observedAt(),
                    decimal(record.open()), decimal(record.high()), decimal(record.low()), decimal(record.close()),
                    longOrNull(record.volume()), decimalOrNull(record.valueVnd()), record.adjustmentStatus(), false)));
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
        if (input.generatedAt() == null || input.rangeStart() == null || input.rangeEnd() == null
                || input.rangeEnd().isBefore(input.rangeStart())) {
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
        for (DailyBarRecord record : input.records()) {
            validateRecord(input, record);
        }
        List<DailyBarRecord> sorted = input.records().stream()
                .sorted(Comparator.comparing(DailyBarRecord::tradingDate)).toList();
        if (!sorted.equals(input.records())) {
            fail("NON_CANONICAL_RECORD_ORDER");
        }
    }

    private static void validateRecord(PackageInput input, DailyBarRecord record) {
        if (record == null || !record.symbol().equals(input.symbol())) {
            fail("INVALID_RECORD");
        }
        if (record.tradingDate() == null || record.observedAt() == null
                || record.tradingDate().isBefore(input.rangeStart()) || record.tradingDate().isAfter(input.rangeEnd())) {
            fail("INVALID_DATE");
        }
        if (!"RAW".equals(record.adjustmentStatus()) && !"PROVIDER_ADJUSTED".equals(record.adjustmentStatus())) {
            fail("INVALID_ADJUSTMENT_STATUS");
        }
        decimal(record.open());
        decimal(record.high());
        decimal(record.low());
        decimal(record.close());
        required(record.canonicalRecord(), "INVALID_RECORD");
    }

    private static BigDecimal decimal(String value) {
        if (value == null || !value.matches("[0-9]+(\\.[0-9]{1,6})?")) {
            fail("INVALID_DECIMAL");
        }
        return new BigDecimal(value).setScale(6, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal decimalOrNull(String value) {
        return value == null || value.isBlank() ? null : decimal(value);
    }

    private static Long longOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            fail("INVALID_VOLUME");
            return null;
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

    public record PackageInput(
            String contractVersion, String toolName, String toolVersion, String upstreamSource, String symbol,
            String packageSha256, String canonicalPayload, Instant generatedAt, LocalDate rangeStart,
            LocalDate rangeEnd, List<DailyBarRecord> records) {
    }

    public record DailyBarRecord(
            String symbol, LocalDate tradingDate, Instant observedAt, String open, String high, String low,
            String close, String volume, String valueVnd, String adjustmentStatus, String canonicalRecord) {
    }

    public record Summary(String symbol, List<IngestionResult> results) {
    }
}
