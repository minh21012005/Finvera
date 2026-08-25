package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.ValuationAssessmentEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.ValuationAssessmentRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    private final Clock clock;

    public ValuationWarmupService(
            EquityProfileRepository equityProfiles,
            MarketReferenceDataService referenceData,
            ValuationAssessmentRepository assessments,
            ValuationService valuations,
            Clock clock) {
        this.equityProfiles = equityProfiles;
        this.referenceData = referenceData;
        this.assessments = assessments;
        this.valuations = valuations;
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
        Map<UUID, LocalDate> lastAssessedByInstrument = assessments
                .findLatestCurrentByInstrumentIdInAndRuleVersion(instrumentIds, ValuationV1.RULE_VERSION).stream()
                .collect(Collectors.toMap(ValuationAssessmentEntity::getInstrumentId,
                        ValuationAssessmentEntity::getAsOfTradingDate, (a, b) -> a.isAfter(b) ? a : b));

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
            if (lastAssessed != null && !lastAssessed.isBefore(today)) {
                skipped++;
                logProgress(processed, instrumentIds.size(), succeeded, skipped, unavailable, failed);
                continue;
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

    private static void logProgress(int processed, int total, int succeeded, int skipped, int unavailable, int failed) {
        if (processed == total || processed % 50 == 0) {
            log.info("valuation_warmup progress processed={} total={} succeeded={} skipped={} unavailable={} failed={}",
                    processed, total, succeeded, skipped, unavailable, failed);
        }
    }

    public record Summary(int total, int succeeded, int skipped, int unavailable, int failed) {
    }
}

