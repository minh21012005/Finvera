package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.entity.EquityPriceObservationEntity;
import com.minhnb.finvera_be.market.entity.MarketImportBatchEntity;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.entity.MarketObservationEntity;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderObservation;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderSnapshotBatch;
import com.minhnb.finvera_be.market.repository.EquityPriceObservationRepository;
import com.minhnb.finvera_be.market.repository.MarketImportBatchRepository;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.market.repository.MarketObservationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

/** Provider-neutral persistence boundary for a verified canonical historical package. */
@Service
@ConditionalOnProperty(name = "finvera.market.import.enabled", havingValue = "true")
public class MarketImportService {
    static final String CONTRACT_VERSION = "vnstock-history-private-bootstrap-v1";
    static final String MARKET_PACKAGE_CONTRACT_VERSION = "vnstock-market-private-package-v1";
    private static final String DATASET = "EQUITY_DAILY_HISTORY";
    private final MarketImportBatchRepository batches;
    private final MarketInstrumentRepository instruments;
    private final MarketObservationRepository observations;
    private final EquityPriceObservationRepository equityPrices;
    private final MarketIngestionService indexIngestion;
    private final Clock clock;

    public MarketImportService(MarketImportBatchRepository batches, MarketInstrumentRepository instruments,
            MarketObservationRepository observations, EquityPriceObservationRepository equityPrices,
            MarketIngestionService indexIngestion, Clock clock) {
        this.batches = batches;
        this.instruments = instruments;
        this.observations = observations;
        this.equityPrices = equityPrices;
        this.indexIngestion = indexIngestion;
        this.clock = clock;
    }

    /** Validates all records before persistence; the transaction makes accepted writes atomic. */
    @Transactional
    public Result importPackage(PackageInput input) {
        validate(input);
        if (batches.existsByPackageSha256(input.packageSha256())) {
            return new Result(Status.ALREADY_APPLIED, input.packageSha256());
        }
        Instant receivedAt = clock.instant();
        ingestIndexRecords(input);
        for (EquityHistoryRecord record : input.records()) {
            BigDecimal close = decimal(record.closePrice());
            MarketInstrumentEntity instrument = instruments.findByVenueAndSymbolAndListedFrom(
                    record.venue(), record.symbol(), record.listedFrom())
                    .orElseGet(() -> instruments.save(new MarketInstrumentEntity(UUID.randomUUID(), record.isin(),
                            record.venue(), record.symbol(), "COMMON_EQUITY", record.listedFrom(), null,
                            record.instrumentStatus(), input.upstreamSource(), input.packageSha256())));
            UUID observationId = UUID.randomUUID();
            String subjectKey = record.venue() + ":" + record.symbol();
            observations.save(new MarketObservationEntity(observationId, input.upstreamSource(), DATASET, subjectKey,
                    record.tradingDate(), record.observedAt(), record.observedAt(), receivedAt,
                    record.sourceSequence(), sha256(record.canonicalRecord()), "ACCEPTED", null, null));
            equityPrices.save(new EquityPriceObservationEntity(UUID.randomUUID(), instrument.getId(), observationId,
                    record.tradingDate(), record.observedAt(), close, null, close, null,
                    record.adjustmentStatus(), null));
        }
        batches.save(new MarketImportBatchEntity(UUID.randomUUID(), input.contractVersion(), input.toolName(),
                input.toolVersion(), input.upstreamSource(), input.packageSha256(), input.rangeStart(), input.rangeEnd(),
                input.generatedAt(), receivedAt, "ACCEPTED", input.records().size() + input.indexRecords().size(), null));
        return new Result(Status.APPLIED, input.packageSha256());
    }

    private void ingestIndexRecords(PackageInput input) {
        Map<IndexBatchKey, List<IndexSnapshotRecord>> grouped = input.indexRecords().stream()
                .collect(Collectors.groupingBy(record -> new IndexBatchKey(record.tradingDate(), record.observedAt(),
                        SessionState.valueOf(record.sessionState()), DataStatus.valueOf(record.dataStatus()))));
        grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> indexIngestion.ingest(new ProviderSnapshotBatch(input.upstreamSource(),
                        entry.getKey().tradingDate(), entry.getKey().observedAt(), entry.getKey().sessionState(),
                        entry.getKey().dataStatus(), List.of("VNSTOCK_PRIVATE_PACKAGE"),
                        entry.getValue().stream().sorted(Comparator.comparing(IndexSnapshotRecord::code))
                                .map(MarketImportService::providerObservation).toList())));
    }

    private static ProviderObservation providerObservation(IndexSnapshotRecord record) {
        return new ProviderObservation(IndexCode.valueOf(record.code()), decimal6(record.level()),
                decimal6(record.referenceLevel()), null, null, parseLong(record.matchedVolume()),
                nullableDecimal4(record.matchedValueVnd()), record.reasonCodes());
    }

    private static void validate(PackageInput input) {
        if (input == null || !(CONTRACT_VERSION.equals(input.contractVersion())
                || MARKET_PACKAGE_CONTRACT_VERSION.equals(input.contractVersion()))) fail("UNSUPPORTED_CONTRACT");
        required(input.toolName(), "INVALID_TOOL"); required(input.toolVersion(), "INVALID_TOOL");
        required(input.upstreamSource(), "INVALID_SOURCE");
        if ("UNKNOWN".equals(input.upstreamSource())) fail("INVALID_SOURCE");
        if (input.generatedAt() == null || input.rangeStart() == null || input.rangeEnd() == null
                || input.rangeEnd().isBefore(input.rangeStart())) fail("INVALID_RANGE");
        if (input.records() == null || input.indexRecords() == null
                || (input.records().isEmpty() && input.indexRecords().isEmpty())) fail("EMPTY_PACKAGE");
        if (input.canonicalPayload() == null || input.canonicalPayload().isBlank()
                || input.packageSha256() == null || !input.packageSha256().matches("[0-9a-f]{64}")
                || !input.packageSha256().equals(sha256(input.canonicalPayload()))) fail("INVALID_CHECKSUM");
        for (EquityHistoryRecord record : input.records()) validateRecord(input, record);
        for (IndexSnapshotRecord record : input.indexRecords()) validateIndexRecord(input, record);
        List<EquityHistoryRecord> sorted = input.records().stream()
                .sorted(Comparator.comparing(EquityHistoryRecord::venue).thenComparing(EquityHistoryRecord::symbol)
                        .thenComparing(EquityHistoryRecord::tradingDate)).toList();
        if (!sorted.equals(input.records())) fail("NON_CANONICAL_RECORD_ORDER");
        for (int index = 0; index < input.records().size(); index++) {
            EquityHistoryRecord record = input.records().get(index);
            if (index > 0) {
                EquityHistoryRecord previous = input.records().get(index - 1);
                if (previous.venue().equals(record.venue()) && previous.symbol().equals(record.symbol())
                        && !record.tradingDate().isAfter(previous.tradingDate())) fail("NON_MONOTONIC_DATE");
            }
        }
    }

    private static void validateRecord(PackageInput input, EquityHistoryRecord record) {
        if (record == null) fail("INVALID_RECORD");
        if (!("HOSE".equals(record.venue()) || "HNX".equals(record.venue()) || "UPCOM".equals(record.venue()))) fail("INVALID_VENUE");
        if (record.symbol() == null || !record.symbol().matches("[A-Z0-9]{1,32}")) fail("INVALID_SYMBOL");
        if (record.listedFrom() == null || record.tradingDate() == null || record.observedAt() == null
                || record.tradingDate().isBefore(record.listedFrom()) || record.tradingDate().isBefore(input.rangeStart())
                || record.tradingDate().isAfter(input.rangeEnd())) fail("INVALID_DATE");
        if (!("ACTIVE".equals(record.instrumentStatus()) || "SUSPENDED".equals(record.instrumentStatus())
                || "DELISTED".equals(record.instrumentStatus()) || "UNKNOWN".equals(record.instrumentStatus()))) fail("INVALID_INSTRUMENT_STATUS");
        if (!("RAW".equals(record.adjustmentStatus()) || "PROVIDER_ADJUSTED".equals(record.adjustmentStatus()))) fail("INVALID_ADJUSTMENT_STATUS");
        decimal(record.closePrice()); required(record.canonicalRecord(), "INVALID_RECORD");
    }

    private static void validateIndexRecord(PackageInput input, IndexSnapshotRecord record) {
        if (record == null) fail("INVALID_INDEX_RECORD");
        try {
            IndexCode.valueOf(record.code());
            SessionState.valueOf(record.sessionState());
            DataStatus.valueOf(record.dataStatus());
        } catch (RuntimeException exception) {
            fail("INVALID_INDEX_RECORD");
        }
        if (record.providerSymbol() == null || !record.providerSymbol().matches("[A-Z0-9]{1,32}")) fail("INVALID_INDEX_SYMBOL");
        if (record.tradingDate() == null || record.observedAt() == null
                || record.tradingDate().isBefore(input.rangeStart()) || record.tradingDate().isAfter(input.rangeEnd())) {
            fail("INVALID_DATE");
        }
        decimal6(record.level());
        decimal6(record.referenceLevel());
        parseLong(record.matchedVolume());
        nullableDecimal4(record.matchedValueVnd());
        if (record.reasonCodes() == null) fail("INVALID_REASON_CODES");
        required(record.canonicalRecord(), "INVALID_RECORD");
    }

    private static BigDecimal decimal(String value) {
        try {
            if (value == null || !value.matches("[0-9]+(\\.[0-9]{1,6})?")) fail("INVALID_DECIMAL");
            return new BigDecimal(value).setScale(6, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) { throw new IllegalArgumentException("INVALID_DECIMAL", exception); }
    }
    private static BigDecimal decimal6(String value) { return decimal(value); }
    private static BigDecimal nullableDecimal4(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (!value.matches("[0-9]+(\\.[0-9]{1,4})?")) fail("INVALID_DECIMAL");
            return new BigDecimal(value).setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) { throw new IllegalArgumentException("INVALID_DECIMAL", exception); }
    }
    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (!value.matches("[0-9]+")) fail("INVALID_INTEGER");
            return Long.parseLong(value);
        } catch (NumberFormatException exception) { throw new IllegalArgumentException("INVALID_INTEGER", exception); }
    }
    private static void required(String value, String code) { if (value == null || value.isBlank()) fail(code); }
    private static void fail(String code) { throw new IllegalArgumentException(code); }
    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private record IndexBatchKey(LocalDate tradingDate, Instant observedAt, SessionState sessionState, DataStatus dataStatus)
            implements Comparable<IndexBatchKey> {
        @Override
        public int compareTo(IndexBatchKey other) {
            int byDate = tradingDate.compareTo(other.tradingDate);
            if (byDate != 0) return byDate;
            int byObservedAt = observedAt.compareTo(other.observedAt);
            if (byObservedAt != 0) return byObservedAt;
            int bySession = sessionState.compareTo(other.sessionState);
            if (bySession != 0) return bySession;
            return dataStatus.compareTo(other.dataStatus);
        }
    }
    public record PackageInput(String contractVersion, String toolName, String toolVersion, String upstreamSource,
            String packageSha256, String canonicalPayload, Instant generatedAt, LocalDate rangeStart,
            LocalDate rangeEnd, List<EquityHistoryRecord> records, List<IndexSnapshotRecord> indexRecords) {
        public PackageInput {
            records = records == null ? List.of() : List.copyOf(records);
            indexRecords = indexRecords == null ? List.of() : List.copyOf(indexRecords);
        }
    }
    public record EquityHistoryRecord(String venue, String symbol, String isin, LocalDate listedFrom,
            String instrumentStatus, LocalDate tradingDate, Instant observedAt, String closePrice,
            String adjustmentStatus, String sourceSequence, String canonicalRecord) { }
    public record IndexSnapshotRecord(String code, String providerSymbol, LocalDate tradingDate, Instant observedAt,
            String sessionState, String dataStatus, String level, String referenceLevel, String matchedVolume,
            String matchedValueVnd, List<String> reasonCodes, String canonicalRecord) {
        public IndexSnapshotRecord {
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        }
    }
    public enum Status { APPLIED, ALREADY_APPLIED }
    public record Result(Status status, String packageSha256) { }
}
