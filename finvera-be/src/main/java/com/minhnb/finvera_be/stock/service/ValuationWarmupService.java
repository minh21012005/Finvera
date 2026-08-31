package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.ValuationAssessmentEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalReportRepository;
import com.minhnb.finvera_be.stock.repository.ValuationAssessmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Owner-triggered backfill for {@code valuation_assessment}. The stock detail
 * endpoint computes and persists valuation on read, but a full data refresh must
 * also materialize the current valuation state for the universe so downstream
 * screens and AI tools read PostgreSQL-derived facts rather than waiting for
 * each detail page to be opened manually.
 */
@Service
@ConditionalOnProperty(name = "finvera.stock.valuation.warmup.enabled", havingValue = "true")
public class ValuationWarmupService {

    private static final Logger log = LoggerFactory.getLogger(ValuationWarmupService.class);
    private static final String LISTED = "LISTED";

    private final EquityProfileRepository equityProfiles;
    private final MarketReferenceDataService referenceData;
    private final ValuationAssessmentRepository assessments;
    private final ValuationService valuations;
    private final EquityDailyBarRepository dailyBars;
    private final FundamentalReportRepository reports;
    private final boolean force;
    private final Clock clock;

    public ValuationWarmupService(
            EquityProfileRepository equityProfiles,
            MarketReferenceDataService referenceData,
            ValuationAssessmentRepository assessments,
            ValuationService valuations,
            EquityDailyBarRepository dailyBars,
            FundamentalReportRepository reports,
            @Value("${finvera.stock.valuation.warmup.force:false}") boolean force,
            Clock clock) {
        this.equityProfiles = equityProfiles;
        this.referenceData = referenceData;
        this.assessments = assessments;
        this.valuations = valuations;
        this.dailyBars = dailyBars;
        this.reports = reports;
        this.force = force;
        this.clock = clock;
    }

    public Summary warmUp() {
        List<UUID> instrumentIds = equityProfiles.findByEffectiveToIsNullAndListingStatus(LISTED).stream()
                .map(EquityProfileEntity::getInstrumentId)
                .toList();
        Map<UUID, InstrumentReference> instrumentsById = referenceData.findInstrumentsByIds(instrumentIds).stream()
                .collect(Collectors.toMap(InstrumentReference::instrumentId, r -> r));

        // Bulk-load existing assessments to skip instruments already assessed today.
        // This single query replaces ~1600 individual findBySymbol calls on routine re-runs
        // where no new data has arrived since the last warmup.
        LocalDate today = LocalDate.now(clock);
        List<ValuationAssessmentEntity> latestAssessments = assessments
                .findLatestCurrentByInstrumentIdInAndRuleVersion(instrumentIds, ValuationV1.RULE_VERSION);
        Map<UUID, LocalDate> lastAssessedByInstrument = latestAssessments.stream()
                .collect(Collectors.toMap(ValuationAssessmentEntity::getInstrumentId,
                        ValuationAssessmentEntity::getAsOfTradingDate, (a, b) -> a.isAfter(b) ? a : b));
        // Q-48 (same defect class as Q-45): "assessed today" is not "inputs unchanged". A bar
        // correction, a history backfill or a report imported after this morning's assessment
        // must trigger a recompute; so must an explicit owner force (calculator fix roll-out
        // within the same rule version, e.g. Q-46/Q-47).
        Map<UUID, Instant> lastCalculatedByInstrument = latestAssessments.stream()
                .filter(a -> a.getCalculatedAt() != null)
                .collect(Collectors.toMap(ValuationAssessmentEntity::getInstrumentId,
                        ValuationAssessmentEntity::getCalculatedAt, (a, b) -> a.isAfter(b) ? a : b));
        Map<UUID, Instant> barsAcceptedByInstrument = latestInstantByInstrument(
                dailyBars.findLatestAcceptedAtByInstrumentIdIn(instrumentIds));
        Map<UUID, Instant> reportsAcceptedByInstrument = latestInstantByInstrument(
                reports.findLatestAcceptedAtByInstrumentIdIn(instrumentIds));
        if (force) {
            log.info("valuation_warmup force=true: every instrument is recomputed");
        }

        int succeeded = 0;
        int skipped = 0;
        int failed = 0;
        int unavailable = 0;
        int processed = 0;
        for (UUID instrumentId : instrumentIds) {
            processed++;
            InstrumentReference reference = instrumentsById.get(instrumentId);
            if (reference == null) {
                failed++;
                logProgress(processed, instrumentIds.size(), succeeded, skipped, unavailable, failed);
                continue;
            }
            // Skip if this instrument already has a current assessment for today — the
            // underlying data (daily bars, fundamentals) has not changed since this same
            // refresh session already imported them, so recomputing would produce the
            // exact same result.
            LocalDate lastAssessed = lastAssessedByInstrument.get(instrumentId);
            boolean assessedToday = lastAssessed != null && !lastAssessed.isBefore(today);
            boolean inputsRevised = inputsRevisedSince(lastCalculatedByInstrument.get(instrumentId),
                    barsAcceptedByInstrument.get(instrumentId), reportsAcceptedByInstrument.get(instrumentId));
            if (assessedToday && !inputsRevised && !force) {
                skipped++;
                logProgress(processed, instrumentIds.size(), succeeded, skipped, unavailable, failed);
                continue;
            }
            if (assessedToday && inputsRevised) {
                log.info("valuation_warmup symbol={} inputs accepted after the last assessment; recomputing",
                        reference.symbol());
            }
            try {
                if (valuations.findBySymbol(reference.symbol()).isPresent()) {
                    succeeded++;
                } else {
                    unavailable++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("valuation_warmup symbol={} failed: {}: {}",
                        reference.symbol(), e.getClass().getSimpleName(), e.getMessage());
            }
            logProgress(processed, instrumentIds.size(), succeeded, skipped, unavailable, failed);
        }
        Summary summary = new Summary(instrumentIds.size(), succeeded, skipped, unavailable, failed);
        log.info("valuation_warmup total={} succeeded={} skipped={} unavailable={} failed={}",
                summary.total(), summary.succeeded(), summary.skipped(), summary.unavailable(), summary.failed());
        return summary;
    }

    /** True when a bar or a report was accepted after the assessment was calculated. */
    static boolean inputsRevisedSince(Instant calculatedAt, Instant latestBarAcceptedAt, Instant latestReportAcceptedAt) {
        if (calculatedAt == null) {
            return false;
        }
        return (latestBarAcceptedAt != null && latestBarAcceptedAt.isAfter(calculatedAt))
                || (latestReportAcceptedAt != null && latestReportAcceptedAt.isAfter(calculatedAt));
    }

    private static Map<UUID, Instant> latestInstantByInstrument(List<Object[]> rows) {
        Map<UUID, Instant> out = new java.util.HashMap<>();
        if (rows == null) {
            return out;
        }
        for (Object[] row : rows) {
            if (row != null && row.length == 2 && row[0] instanceof UUID id && row[1] instanceof Instant at) {
                out.put(id, at);
            }
        }
        return out;
    }

    private static void logProgress(int processed, int total, int succeeded, int skipped, int unavailable, int failed) {
        if (processed == total || processed % 50 == 0) {
            log.info("valuation_warmup progress processed={} total={} succeeded={} skipped={} unavailable={} failed={}",
                    processed, total, succeeded, skipped, unavailable, failed);
        }
    }

    public record Summary(int total, int succeeded, int skipped, int unavailable, int failed) {
    }
}

