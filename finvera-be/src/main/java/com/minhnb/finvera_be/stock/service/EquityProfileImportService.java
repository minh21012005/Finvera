package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-operated, offline import boundary for the bootstrap company-profile snapshot produced by
 * {@code tools/market-data/vnstock-export/export_equity_profile.py} (ADR-0004). Creates one
 * current {@link EquityProfileEntity} per symbol that has an active instrument and does not
 * already have a current profile row -- never edits or supersedes an existing profile, so it is
 * safe to re-run. Sector linkage is left to {@link SectorReferenceImportService}, which requires
 * exactly this row to already exist.
 */
@Service
@ConditionalOnProperty(name = "finvera.stock.import.equity-profile.enabled", havingValue = "true")
public class EquityProfileImportService {

    static final String CONTRACT_VERSION = "vnstock-equity-profile-v1";
    private static final String SOURCE_PREFIX = "VNSTOCK";

    private final MarketReferenceDataService referenceData;
    private final EquityProfileRepository profiles;

    public EquityProfileImportService(MarketReferenceDataService referenceData, EquityProfileRepository profiles) {
        this.referenceData = referenceData;
        this.profiles = profiles;
    }

    @Transactional
    public Summary importPackage(PackageInput input) {
        validate(input);
        List<ProfileResult> results = new ArrayList<>(input.records().size());
        for (ProfileRecord record : input.records()) {
            var instrumentOpt = referenceData.findActiveInstrumentBySymbol(record.symbol());
            if (instrumentOpt.isEmpty()) {
                results.add(new ProfileResult(record.symbol(), ProfileStatus.UNKNOWN_INSTRUMENT));
                continue;
            }
            UUID instrumentId = instrumentOpt.get().instrumentId();
            var current = profiles.findFirstByInstrumentIdAndEffectiveToIsNull(instrumentId);
            if (current.isPresent()) {
                // Feature 010 FR-001: a profile is revised (effective-dated) only when a share count
                // arrives for a row that has none, or the count changed. Names alone never revise.
                EquityProfileEntity existing = current.orElseThrow();
                boolean sharesArrived = record.sharesOutstanding() != null
                        && !record.sharesOutstanding().equals(existing.getSharesOutstanding());
                if (!sharesArrived) {
                    results.add(new ProfileResult(record.symbol(), ProfileStatus.ALREADY_PRESENT));
                    continue;
                }
                existing.closeAt(record.effectiveFrom());
                profiles.saveAndFlush(existing);
                profiles.save(new EquityProfileEntity(UUID.randomUUID(), instrumentId, record.companyNameVi(),
                        record.companyNameEn(), existing.getSectorReferenceId(), record.sharesOutstanding(),
                        record.freeFloatRatio(), record.listingStatus(), record.effectiveFrom(), null,
                        input.upstreamSource(), input.packageSha256(), null));
                results.add(new ProfileResult(record.symbol(), ProfileStatus.UPDATED));
                continue;
            }
            profiles.save(new EquityProfileEntity(UUID.randomUUID(), instrumentId, record.companyNameVi(),
                    record.companyNameEn(), null, record.sharesOutstanding(), record.freeFloatRatio(),
                    record.listingStatus(), record.effectiveFrom(), null,
                    input.upstreamSource(), input.packageSha256(),
                    record.sharesOutstanding() != null ? null : record.qualityReason()));
            results.add(new ProfileResult(record.symbol(), ProfileStatus.CREATED));
        }
        return new Summary(results);
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
        for (ProfileRecord record : input.records()) {
            if (record.symbol() == null || !record.symbol().matches("[A-Z0-9]{1,32}")) {
                fail("INVALID_SYMBOL");
            }
            required(record.companyNameVi(), "INVALID_COMPANY_NAME");
            if (!("LISTED".equals(record.listingStatus()) || "SUSPENDED".equals(record.listingStatus())
                    || "HALTED".equals(record.listingStatus()) || "DELISTED".equals(record.listingStatus())
                    || "UNKNOWN".equals(record.listingStatus()))) {
                fail("INVALID_LISTING_STATUS");
            }
            if (record.effectiveFrom() == null) {
                fail("INVALID_DATE");
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

    public record PackageInput(String contractVersion, String toolName, String toolVersion, String upstreamSource,
            String packageSha256, String canonicalPayload, Instant generatedAt, List<ProfileRecord> records) {
    }

    public record ProfileRecord(String symbol, String companyNameVi, String companyNameEn, String listingStatus,
            LocalDate effectiveFrom, String qualityReason, String canonicalRecord,
            Long sharesOutstanding, java.math.BigDecimal freeFloatRatio) {
        public ProfileRecord(String symbol, String companyNameVi, String companyNameEn, String listingStatus,
                LocalDate effectiveFrom, String qualityReason, String canonicalRecord) {
            this(symbol, companyNameVi, companyNameEn, listingStatus, effectiveFrom, qualityReason, canonicalRecord,
                    null, null);
        }
    }

    public enum ProfileStatus { CREATED, UPDATED, ALREADY_PRESENT, UNKNOWN_INSTRUMENT }

    public record ProfileResult(String symbol, ProfileStatus status) {
    }

    public record Summary(List<ProfileResult> results) {
    }
}
