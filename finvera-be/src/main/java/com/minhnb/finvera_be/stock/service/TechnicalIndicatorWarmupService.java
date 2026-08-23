package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Owner-triggered, one-time-ish backfill: calls {@link TechnicalIndicatorService#findBySymbol}
 * for every {@code LISTED} instrument so {@code technical_indicator_result} has a row before
 * anyone opens that stock's own detail page. {@link
 * com.minhnb.finvera_be.stock.service.strategy.StrategyScanService} reads only that persisted
 * table (never recomputes live), so a freshly bulk-imported symbol shows every MA/MACD/RSI-based
 * strategy as {@code INSUFFICIENT_HISTORY} on the scan page until this warmup (or an individual
 * page view) runs at least once for it — this reuses that exact same computation/persistence path,
 * it does not duplicate any indicator logic.
 */
@Service
@ConditionalOnProperty(name = "finvera.stock.technical.warmup.enabled", havingValue = "true")
public class TechnicalIndicatorWarmupService {

    private static final Logger log = LoggerFactory.getLogger(TechnicalIndicatorWarmupService.class);
    private static final String LISTED = "LISTED";

    private final EquityProfileRepository equityProfiles;
    private final MarketReferenceDataService referenceData;
    private final TechnicalIndicatorService technicalIndicators;

    public TechnicalIndicatorWarmupService(EquityProfileRepository equityProfiles,
            MarketReferenceDataService referenceData, TechnicalIndicatorService technicalIndicators) {
        this.equityProfiles = equityProfiles;
        this.referenceData = referenceData;
        this.technicalIndicators = technicalIndicators;
    }

    public Summary warmUp() {
        List<UUID> instrumentIds = equityProfiles.findByEffectiveToIsNullAndListingStatus(LISTED).stream()
                .map(EquityProfileEntity::getInstrumentId).toList();
        Map<UUID, InstrumentReference> instrumentsById = referenceData.findInstrumentsByIds(instrumentIds).stream()
                .collect(Collectors.toMap(InstrumentReference::instrumentId, r -> r));

        int succeeded = 0;
        int failed = 0;
        for (UUID instrumentId : instrumentIds) {
            InstrumentReference reference = instrumentsById.get(instrumentId);
            if (reference == null) {
                failed++;
                continue;
            }
            try {
                technicalIndicators.findBySymbol(reference.symbol());
                succeeded++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("technical_indicator_warmup symbol={} failed: {}: {}",
                        reference.symbol(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
        Summary summary = new Summary(instrumentIds.size(), succeeded, failed);
        log.info("technical_indicator_warmup total={} succeeded={} failed={}",
                summary.total(), summary.succeeded(), summary.failed());
        return summary;
    }

    public record Summary(int total, int succeeded, int failed) {
    }
}
