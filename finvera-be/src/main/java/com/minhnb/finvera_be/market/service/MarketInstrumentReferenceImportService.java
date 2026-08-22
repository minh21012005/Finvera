package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.entity.MarketImportBatchEntity;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketImportBatchRepository;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers the tradable-symbol universe as {@code market_instrument} rows so downstream stock
 * ingestion (Feature 002's {@link com.minhnb.finvera_be.stock.service.StockIngestionService}) can
 * find an active instrument per symbol instead of rejecting with UNKNOWN_INSTRUMENT. Only creates
 * rows that don't already have an active (listed_to is null) entry -- it never edits or supersedes
 * an existing instrument, so it is safe to re-run against a growing or already-populated table.
 */
@Service
@ConditionalOnProperty(name = "finvera.market.import.instrument-reference.enabled", havingValue = "true")
public class MarketInstrumentReferenceImportService {
    static final String CONTRACT_VERSION = "vnstock-instrument-reference-v1";
    private final MarketImportBatchRepository batches;
    private final MarketInstrumentRepository instruments;
    private final Clock clock;

    public MarketInstrumentReferenceImportService(MarketImportBatchRepository batches,
            MarketInstrumentRepository instruments, Clock clock) {
        this.batches = batches;
        this.instruments = instruments;
        this.clock = clock;
    }

    @Transactional
    public Result importPackage(PackageInput input) {
        validate(input);
        if (batches.existsByPackageSha256(input.packageSha256())) {
            return new Result(Status.ALREADY_APPLIED, input.packageSha256(), 0, 0);
        }
        Instant receivedAt = clock.instant();
        int registered = 0;
        int skipped = 0;
        for (InstrumentReferenceRecord record : input.records()) {
            if (instruments.findFirstBySymbolAndListedToIsNull(record.symbol()).isPresent()) {
                skipped++;
                continue;
            }
            instruments.save(new MarketInstrumentEntity(UUID.randomUUID(), record.isin(), record.venue(),
                    record.symbol(), "COMMON_EQUITY", record.listedFrom(), null, record.instrumentStatus(),
                    input.upstreamSource(), input.packageSha256()));
            registered++;
        }
        // No natural multi-day range exists for a point-in-time reference snapshot; record the
        // generation date as both endpoints rather than fabricating a range.
        LocalDate asOf = input.generatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        batches.save(new MarketImportBatchEntity(UUID.randomUUID(), input.contractVersion(), input.toolName(),
                input.toolVersion(), input.upstreamSource(), input.packageSha256(), asOf, asOf,
                input.generatedAt(), receivedAt, "ACCEPTED", input.records().size(), null));
        return new Result(Status.APPLIED, input.packageSha256(), registered, skipped);
    }

    private static void validate(PackageInput input) {
        if (input == null || !CONTRACT_VERSION.equals(input.contractVersion())) fail("UNSUPPORTED_CONTRACT");
        required(input.toolName(), "INVALID_TOOL");
        required(input.toolVersion(), "INVALID_TOOL");
        required(input.upstreamSource(), "INVALID_SOURCE");
        if ("UNKNOWN".equals(input.upstreamSource())) fail("INVALID_SOURCE");
        if (input.generatedAt() == null) fail("INVALID_RANGE");
        if (input.records() == null || input.records().isEmpty()) fail("EMPTY_PACKAGE");
        if (input.canonicalPayload() == null || input.canonicalPayload().isBlank()
                || input.packageSha256() == null || !input.packageSha256().matches("[0-9a-f]{64}")
                || !input.packageSha256().equals(sha256(input.canonicalPayload()))) fail("INVALID_CHECKSUM");
        for (InstrumentReferenceRecord record : input.records()) validateRecord(record);
        List<InstrumentReferenceRecord> sorted = input.records().stream()
                .sorted(Comparator.comparing(InstrumentReferenceRecord::venue)
                        .thenComparing(InstrumentReferenceRecord::symbol)).toList();
        if (!sorted.equals(input.records())) fail("NON_CANONICAL_RECORD_ORDER");
        for (int index = 1; index < input.records().size(); index++) {
            InstrumentReferenceRecord previous = input.records().get(index - 1);
            InstrumentReferenceRecord record = input.records().get(index);
            if (previous.venue().equals(record.venue()) && previous.symbol().equals(record.symbol())) fail("DUPLICATE_SYMBOL");
        }
    }

    private static void validateRecord(InstrumentReferenceRecord record) {
        if (record == null) fail("INVALID_RECORD");
        if (!("HOSE".equals(record.venue()) || "HNX".equals(record.venue()) || "UPCOM".equals(record.venue()))) fail("INVALID_VENUE");
        if (record.symbol() == null || !record.symbol().matches("[A-Z0-9]{1,32}")) fail("INVALID_SYMBOL");
        if (record.listedFrom() == null) fail("INVALID_DATE");
        if (!("ACTIVE".equals(record.instrumentStatus()) || "SUSPENDED".equals(record.instrumentStatus())
                || "DELISTED".equals(record.instrumentStatus()) || "UNKNOWN".equals(record.instrumentStatus()))) fail("INVALID_INSTRUMENT_STATUS");
        required(record.canonicalRecord(), "INVALID_RECORD");
    }

    private static void required(String value, String code) { if (value == null || value.isBlank()) fail(code); }
    private static void fail(String code) { throw new IllegalArgumentException(code); }
    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    public record PackageInput(String contractVersion, String toolName, String toolVersion, String upstreamSource,
            String packageSha256, String canonicalPayload, Instant generatedAt, List<InstrumentReferenceRecord> records) { }
    public record InstrumentReferenceRecord(String venue, String symbol, String isin, LocalDate listedFrom,
            String instrumentStatus, String canonicalRecord) { }
    public enum Status { APPLIED, ALREADY_APPLIED }
    public record Result(Status status, String packageSha256, int registered, int skipped) { }
}
