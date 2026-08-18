package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.domain.overview.StockOverviewCalculator;
import com.minhnb.finvera_be.stock.domain.overview.StockOverviewCalculator.StockOverviewResult;
import com.minhnb.finvera_be.stock.domain.time.StockFreshnessPolicy;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.SectorReferenceEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.SectorReferenceRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-001, FR-002, FR-013; DATA-001, DATA-002, DATA-005, NFR-003. Assembles
 * one coherent overview read model from accepted facts only. "Current
 * price" is the latest accepted completed-session daily bar: the live TCBS
 * quote path is gated behind G-03 (research R-012) and not wired yet, so
 * this deliberately falls back to the last accepted end-of-day bar rather
 * than fabricating an intraday value.
 */
@Service
public class StockOverviewService {

    private final MarketReferenceDataService referenceData;
    private final EquityProfileRepository profiles;
    private final EquityDailyBarRepository dailyBars;
    private final SectorReferenceRepository sectors;
    private final StockFreshnessPolicy freshnessPolicy = new StockFreshnessPolicy();
    private final StockOverviewCalculator calculator = new StockOverviewCalculator();
    private final Clock clock;

    public StockOverviewService(
            MarketReferenceDataService referenceData,
            EquityProfileRepository profiles,
            EquityDailyBarRepository dailyBars,
            SectorReferenceRepository sectors,
            Clock clock) {
        this.referenceData = referenceData;
        this.profiles = profiles;
        this.dailyBars = dailyBars;
        this.sectors = sectors;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<StockOverview> findBySymbol(String symbol) {
        Optional<InstrumentReference> instrument = referenceData.findActiveInstrumentBySymbol(symbol);
        if (instrument.isEmpty()) {
            return Optional.empty();
        }
        InstrumentReference reference = instrument.orElseThrow();
        Optional<EquityProfileEntity> profile = profiles.findFirstByInstrumentIdAndEffectiveToIsNull(
                reference.instrumentId());
        Optional<EquityDailyBarEntity> latestBar = dailyBars
                .findFirstByInstrumentIdAndCurrentTrueOrderByTradingDateDescAcceptedAtDesc(reference.instrumentId());
        Optional<EquityDailyBarEntity> previousBar = latestBar.flatMap(bar -> dailyBars
                .findFirstByInstrumentIdAndCurrentTrueAndTradingDateBeforeOrderByTradingDateDesc(
                        reference.instrumentId(), bar.getTradingDate()));

        StockOverviewResult price = calculator.calculate(new StockOverviewCalculator.Input(
                latestBar.map(EquityDailyBarEntity::getClosePrice).orElse(null),
                previousBar.map(EquityDailyBarEntity::getClosePrice).orElse(null),
                latestBar.map(EquityDailyBarEntity::getVolume).orElse(null),
                latestBar.map(EquityDailyBarEntity::getValueVnd).orElse(null),
                profile.map(EquityProfileEntity::getSharesOutstanding).orElse(null)));

        Instant asOf = clock.instant();
        var session = referenceData.resolveSession(reference.venue(), asOf);
        DataStatus dataStatus = evaluateOverviewFreshness(latestBar, session.tradingDate());

        List<String> reasonCodes = new ArrayList<>();
        if (profile.isEmpty()) {
            reasonCodes.add("PROFILE_UNAVAILABLE");
        }
        if (latestBar.isEmpty()) {
            reasonCodes.add("PRICE_UNAVAILABLE");
        }
        if (price.changeBasisReason() != null) {
            reasonCodes.add(price.changeBasisReason());
        }

        String coherenceKey = CoherenceKeys.of(List.of(
                profile.map(p -> p.getId().toString()).orElse(""),
                latestBar.map(b -> b.getId() + ":" + b.getRevision()).orElse(""),
                previousBar.map(b -> b.getId() + ":" + b.getRevision()).orElse("")));

        Optional<SectorReferenceEntity> sector = profile.map(EquityProfileEntity::getSectorReferenceId)
                .flatMap(sectors::findById);

        return Optional.of(new StockOverview(reference.symbol(), reference.venue(),
                profile.map(EquityProfileEntity::getCompanyNameVi).orElse(null),
                profile.map(EquityProfileEntity::getCompanyNameEn).orElse(null),
                profile.map(EquityProfileEntity::getListingStatus).orElse(reference.status()),
                sector.map(SectorReferenceEntity::getDisplayNameVi).orElse(null),
                sector.map(SectorReferenceEntity::getScheme).orElse(null),
                profile.map(EquityProfileEntity::getSharesOutstanding).orElse(null),
                price, session.state(),
                latestBar.map(EquityDailyBarEntity::getTradingDate).orElse(session.tradingDate()),
                asOf, dataStatus, List.copyOf(reasonCodes), coherenceKey));
    }

    private DataStatus evaluateOverviewFreshness(Optional<EquityDailyBarEntity> latestBar, LocalDate asOfTradingDate) {
        if (latestBar.isEmpty()) {
            return freshnessPolicy.evaluateMissing();
        }
        int sessionsBehind = countWeekdaysBetween(latestBar.orElseThrow().getTradingDate(), asOfTradingDate);
        return freshnessPolicy.evaluateDailyBarSeries(sessionsBehind);
    }

    /**
     * Approximates completed-sessions-behind by counting weekdays, since exact
     * trading-calendar day counting (holiday-aware) is not wired into this
     * service yet. This is directionally correct for the fixture-mode paths
     * this feature currently exercises; replace with an exact
     * {@code market_calendar_day} range count if this proves too coarse once
     * live sessions are enabled.
     */
    private static int countWeekdaysBetween(LocalDate lastAccepted, LocalDate asOfTradingDate) {
        int count = 0;
        LocalDate cursor = lastAccepted;
        while (cursor.isBefore(asOfTradingDate)) {
            cursor = cursor.plusDays(1);
            if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }

    public record StockOverview(
            String symbol,
            String venue,
            String companyNameVi,
            String companyNameEn,
            String listingStatus,
            String sector,
            String sectorScheme,
            Long sharesOutstanding,
            StockOverviewResult price,
            SessionState sessionState,
            LocalDate tradingDate,
            Instant asOf,
            DataStatus dataStatus,
            List<String> reasonCodes,
            String coherenceKey) {
    }
}
