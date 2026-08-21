package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.stock.entity.SectorReferenceEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.SectorReferenceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-operated, offline import boundary for the KBS sector classification produced by
 * {@code tools/market-data/vnstock-export/export_sector_reference.py} (ADR-0004; research.md
 * R-012 gate G-04, owner-accepted). Creates/reuses one {@link SectorReferenceEntity} per (scheme,
 * schemeVersion, sectorCode) and backfills {@code equity_profile.sector_reference_id} for every
 * classified symbol that already has an active instrument and equity profile — symbols Finvera
 * has not onboarded yet are skipped and counted, never fabricated.
 */
@Service
@ConditionalOnProperty(name = "finvera.stock.import.sector-reference.enabled", havingValue = "true")
public class SectorReferenceImportService {

    static final String CONTRACT_VERSION = "vnstock-sector-reference-v1";
    private static final String SOURCE_PREFIX = "VNSTOCK";

    private final MarketReferenceDataService referenceData;
    private final SectorReferenceRepository sectors;
    private final EquityProfileRepository profiles;

    public SectorReferenceImportService(
            MarketReferenceDataService referenceData, SectorReferenceRepository sectors,
            EquityProfileRepository profiles) {
        this.referenceData = referenceData;
        this.sectors = sectors;
        this.profiles = profiles;
    }

    @Transactional
    public Summary importPackage(PackageInput input) {
        validate(input);
        Map<String, SectorReferenceEntity> sectorByCode = new HashMap<>();
        List<ClassificationResult> results = new ArrayList<>(input.records().size());

        for (ClassificationRecord record : input.records()) {
            SectorReferenceEntity sectorRef = sectorByCode.computeIfAbsent(record.sectorCode(), code ->
                    sectors.findBySchemeAndSchemeVersionAndSectorCode(input.scheme(), input.schemeVersion(), code)
                            .orElseGet(() -> sectors.save(new SectorReferenceEntity(
                                    UUID.randomUUID(), input.scheme(), input.schemeVersion(), code,
                                    record.displayNameVi(), record.displayNameEn()))));

            var instrumentOpt = referenceData.findActiveInstrumentBySymbol(record.symbol());
            if (instrumentOpt.isEmpty()) {
                results.add(new ClassificationResult(record.symbol(), ClassificationStatus.UNKNOWN_INSTRUMENT));
                continue;
            }
            int updated = profiles.updateSectorReferenceId(instrumentOpt.get().instrumentId(), sectorRef.getId());
            results.add(new ClassificationResult(record.symbol(),
                    updated > 0 ? ClassificationStatus.CLASSIFIED : ClassificationStatus.NO_EQUITY_PROFILE));
        }
        return new Summary(input.scheme(), input.schemeVersion(), results);
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
        required(input.scheme(), "INVALID_SCHEME");
        required(input.schemeVersion(), "INVALID_SCHEME_VERSION");
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
        for (ClassificationRecord record : input.records()) {
            if (record.symbol() == null || !record.symbol().matches("[A-Z0-9]{1,32}")) {
                fail("INVALID_SYMBOL");
            }
            required(record.sectorCode(), "INVALID_SECTOR_CODE");
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

    public record PackageInput(
            String contractVersion, String toolName, String toolVersion, String upstreamSource, String scheme,
            String schemeVersion, String packageSha256, String canonicalPayload, Instant generatedAt,
            List<ClassificationRecord> records) {
    }

    public record ClassificationRecord(
            String symbol, String sectorCode, String displayNameVi, String displayNameEn, String canonicalRecord) {
    }

    public enum ClassificationStatus {
        CLASSIFIED, UNKNOWN_INSTRUMENT, NO_EQUITY_PROFILE
    }

    public record ClassificationResult(String symbol, ClassificationStatus status) {
    }

    public record Summary(String scheme, String schemeVersion, List<ClassificationResult> results) {
    }
}
