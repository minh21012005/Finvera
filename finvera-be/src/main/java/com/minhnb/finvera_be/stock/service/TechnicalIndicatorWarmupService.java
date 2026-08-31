package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorResultEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorResultRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Owner-triggered backfill: calls {@link TechnicalIndicatorService#findBySymbol} for every
 * {@code LISTED} instrument so {@code technical_indicator_result} has a row before anyone opens
 * that stock's own detail page. {@link
 * com.minhnb.finvera_be.stock.service.strategy.StrategyScanService} reads only that persisted
 * table (never recomputes live), so a freshly bulk-imported symbol shows every strategy as
 * {@code INSUFFICIENT_HISTORY} on the scan page until this warmup (or an individual page view)
 * runs at least once for it — this reuses that exact same computation/persistence path, it does
 * not duplicate any indicator logic.
 *
 * <p>The three crossing strategies (MA/MACD/RSI-based) additionally need a "prior trading day"
 * indicator snapshot to detect a cross. A single as-of-today computation can never produce that on
 * its own; ordinarily it accumulates one real trading day at a time as the app is used live. This
 * backfill instead walks every trading day the instrument has priced data for but no indicator
 * row for yet — either since the last time this ran (a short, cheap gap for routine re-runs) or,
 * for an instrument with no indicator history at all yet, the latest {@value #BOOTSTRAP_BARS}
 * available trading days — so a long gap between runs (the owner stepping away for a week, say)
 * still ends up with a genuine, non-fabricated "yesterday" snapshot instead of comparing against
 * whatever was last computed days or weeks earlier.
 */
@Service
@ConditionalOnProperty(name = "finvera.stock.technical.warmup.enabled", havingValue = "true")
public class TechnicalIndicatorWarmupService {

    private static final Logger log = LoggerFactory.getLogger(TechnicalIndicatorWarmupService.class);
    private static final String LISTED = "LISTED";
    private static final int BOOTSTRAP_BARS = 30;

    private final EquityProfileRepository equityProfiles;
    private final MarketReferenceDataService referenceData;
    private final EquityDailyBarRepository dailyBars;
    private final TechnicalIndicatorResultRepository indicatorResults;
    private final TechnicalIndicatorService technicalIndicators;

    public TechnicalIndicatorWarmupService(EquityProfileRepository equityProfiles,
            MarketReferenceDataService referenceData, EquityDailyBarRepository dailyBars,
            TechnicalIndicatorResultRepository indicatorResults, TechnicalIndicatorService technicalIndicators) {
        this.equityProfiles = equityProfiles;
        this.referenceData = referenceData;
        this.dailyBars = dailyBars;
        this.indicatorResults = indicatorResults;
        this.technicalIndicators = technicalIndicators;
    }

    public Summary warmUp() {
        List<UUID> instrumentIds = equityProfiles.findByEffectiveToIsNullAndListingStatus(LISTED).stream()
                .map(EquityProfileEntity::getInstrumentId).toList();
        Map<UUID, InstrumentReference> instrumentsById = referenceData.findInstrumentsByIds(instrumentIds).stream()
                .collect(Collectors.toMap(InstrumentReference::instrumentId, r -> r));

        Map<UUID, LocalDate> lastComputedByInstrument = indicatorResults
                .findByInstrumentIdInAndRuleVersionAndCurrentTrue(instrumentIds, TechnicalIndicatorsV1.RULE_VERSION)
                .stream()
                .collect(Collectors.toMap(TechnicalIndicatorResultEntity::getInstrumentId,
                        TechnicalIndicatorResultEntity::getAsOfTradingDate, (a, b) -> a.isAfter(b) ? a : b));

        Map<UUID, List<EquityDailyBarEntity>> recentBarsByInstrument = dailyBars
                .findLatestNCurrentByInstrumentIdIn(instrumentIds, BOOTSTRAP_BARS).stream()
                .collect(java.util.stream.Collectors.groupingBy(EquityDailyBarEntity::getInstrumentId));

        // Q-45: "no newer trading date" is not "no new data". A provider correction of an old
        // bar or a history backfill re-imports rows (new accepted_at) without moving the latest
        // date; the stored indicators are then stale (wrong MA/RSI, or INSUFFICIENT_HISTORY on an
        // instrument that now has 250+ bars). Recompute whenever any current bar was accepted
        // after the latest result was calculated.
        Map<UUID, Instant> lastCalculatedByInstrument = indicatorResults
                .findByInstrumentIdInAndRuleVersionAndCurrentTrue(instrumentIds, TechnicalIndicatorsV1.RULE_VERSION)
                .stream()
                .filter(r -> r.getCalculatedAt() != null)
                .collect(Collectors.toMap(TechnicalIndicatorResultEntity::getInstrumentId,
                        TechnicalIndicatorResultEntity::getCalculatedAt, (a, b) -> a.isAfter(b) ? a : b));
        Map<UUID, Instant> barsAcceptedByInstrument = new java.util.HashMap<>();
        for (Object[] row : dailyBars.findLatestAcceptedAtByInstrumentIdIn(instrumentIds)) {
            if (row != null && row.length == 2 && row[0] instanceof UUID id && row[1] instanceof Instant at) {
                barsAcceptedByInstrument.put(id, at);
            }
        }

        int succeeded = 0;
        int skipped = 0;
        int failed = 0;
        int processed = 0;
        for (UUID instrumentId : instrumentIds) {
            processed++;
            InstrumentReference reference = instrumentsById.get(instrumentId);
            if (reference == null) {
                failed++;
                logProgress(processed, instrumentIds.size(), succeeded, skipped, failed);
                continue;
            }
            try {
                List<LocalDate> datesToBackfill = datesToBackfill(recentBarsByInstrument.get(instrumentId),
                        lastComputedByInstrument.get(instrumentId));
                boolean barsRevised = barsRevisedSinceLastResult(barsAcceptedByInstrument.get(instrumentId),
                        lastCalculatedByInstrument.get(instrumentId));
                if (datesToBackfill.isEmpty() && !barsRevised) {
                    // Already up-to-date: last computed date >= latest bar date AND no bar was
                    // accepted since the result was calculated. Skip the expensive findBySymbol
                    // round-trip entirely — the recomputed row would be identical.
                    skipped++;
                    logProgress(processed, instrumentIds.size(), succeeded, skipped, failed);
                    continue;
                }
                if (datesToBackfill.isEmpty()) {
                    log.info("technical_indicator_warmup symbol={} bars revised after last result; recomputing latest",
                            reference.symbol());
                }
                for (int i = 0; i < datesToBackfill.size() - 1; i++) {
                    technicalIndicators.findBySymbol(reference.symbol(), datesToBackfill.get(i));
                }
                technicalIndicators.findBySymbol(reference.symbol());
                succeeded++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("technical_indicator_warmup symbol={} failed: {}: {}",
                        reference.symbol(), e.getClass().getSimpleName(), e.getMessage());
            }
            logProgress(processed, instrumentIds.size(), succeeded, skipped, failed);
        }
        Summary summary = new Summary(instrumentIds.size(), succeeded, skipped, failed);
        log.info("technical_indicator_warmup total={} succeeded={} skipped={} failed={}",
                summary.total(), summary.succeeded(), summary.skipped(), summary.failed());
        return summary;
    }

    private static void logProgress(int processed, int total, int succeeded, int skipped, int failed) {
        if (processed == total || processed % 50 == 0) {
            log.info("technical_indicator_warmup progress processed={} total={} succeeded={} skipped={} failed={}",
                    processed, total, succeeded, skipped, failed);
        }
    }

    /**
     * Trading dates (ascending) this instrument has priced data for but no indicator row for yet,
     * always ending with the latest available date (computed by the caller's own final,
     * no-cutoff call so it reflects every current bar, not just the fetched window). Never more
     * than {@value #BOOTSTRAP_BARS} entries even for an instrument with no history at all yet, so
     * a brand-new symbol costs a bounded amount of work rather than its entire multi-year history.
     */
    static boolean barsRevisedSinceLastResult(Instant latestBarAcceptedAt, Instant lastCalculatedAt) {
        return latestBarAcceptedAt != null && lastCalculatedAt != null && latestBarAcceptedAt.isAfter(lastCalculatedAt);
    }

    private static List<LocalDate> datesToBackfill(List<EquityDailyBarEntity> recentBars, LocalDate lastComputed) {
        if (recentBars == null || recentBars.isEmpty()) {
            return List.of();
        }
        List<LocalDate> ascendingDates = recentBars.stream().map(EquityDailyBarEntity::getTradingDate)
                .sorted(Comparator.naturalOrder()).toList();
        if (lastComputed == null) {
            return ascendingDates;
        }
        return ascendingDates.stream().filter(date -> date.isAfter(lastComputed)).toList();
    }

    public record Summary(int total, int succeeded, int skipped, int failed) {
    }
}
