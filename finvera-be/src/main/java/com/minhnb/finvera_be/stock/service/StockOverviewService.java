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
import com.minhnb.finvera_be.stock.provider.StockQuoteProvider.ProviderAuthenticationRequiredException;
import com.minhnb.finvera_be.stock.provider.tcbs.TcbsStockQuoteProvider;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.SectorReferenceRepository;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import com.minhnb.finvera_be.stock.service.StockObservabilityService.FailureCategory;
import com.minhnb.finvera_be.stock.service.StockObservabilityService.StockFailureReason;
import com.minhnb.finvera_be.stock.service.StockObservabilityService.StockOperation;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-001, FR-002, FR-013; DATA-001, DATA-002, DATA-005, NFR-003. Assembles
 * one coherent overview read model from accepted facts only. "Current
 * price" is the latest accepted completed-session daily bar. When {@code
 * finvera.stock.provider.quote-live-enabled} is true (research R-012 gate
 * G-03, closed 2026-08-22), a live TCBS quote is fetched and ingested as
 * today's bar before that read — the response still comes entirely from
 * PostgreSQL, never directly from the provider call (Constitution Principle
 * II: a live value is never the only authoritative copy). A failed live
 * fetch degrades gracefully to whatever is already persisted (Principle VII)
 * rather than failing the whole overview.
 */
@Service
public class StockOverviewService {

    private static final Logger log = LoggerFactory.getLogger(StockOverviewService.class);

    private final MarketReferenceDataService referenceData;
    private final EquityProfileRepository profiles;
    private final EquityDailyBarRepository dailyBars;
    private final SectorReferenceRepository sectors;
    private final Optional<TcbsStockQuoteProvider> liveQuoteProvider;
    private final StockIngestionService ingestion;
    private final StockObservabilityService observability;
    private final StockFreshnessPolicy freshnessPolicy = new StockFreshnessPolicy();
    private final StockOverviewCalculator calculator = new StockOverviewCalculator();
    private final Clock clock;
    private final boolean quoteLiveEnabled;

    public StockOverviewService(
            MarketReferenceDataService referenceData,
            EquityProfileRepository profiles,
            EquityDailyBarRepository dailyBars,
            SectorReferenceRepository sectors,
            Optional<TcbsStockQuoteProvider> liveQuoteProvider,
            StockIngestionService ingestion,
            StockObservabilityService observability,
            Clock clock,
            @Value("${finvera.stock.provider.quote-live-enabled:false}") boolean quoteLiveEnabled) {
        this.referenceData = referenceData;
        this.profiles = profiles;
        this.dailyBars = dailyBars;
        this.sectors = sectors;
        this.liveQuoteProvider = liveQuoteProvider;
        this.ingestion = ingestion;
        this.observability = observability;
        this.clock = clock;
        this.quoteLiveEnabled = quoteLiveEnabled;
    }

    @Transactional
    public Optional<StockOverview> findBySymbol(String symbol) {
        Optional<InstrumentReference> instrument = referenceData.findActiveInstrumentBySymbol(symbol);
        if (instrument.isEmpty()) {
            return Optional.empty();
        }
        InstrumentReference reference = instrument.orElseThrow();

        if (quoteLiveEnabled && liveQuoteProvider.isPresent()) {
            refreshFromLiveQuote(liveQuoteProvider.get(), reference);
        }

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

    /**
     * Fetches and ingests today's live TCBS bar before the read below, so the response still
     * comes only from what {@code equity_daily_bar} now holds. Every failure mode (auth expired,
     * connectivity, TCBS omitting the symbol, an unusable price) is caught here and degrades to
     * "keep serving the last accepted bar" — this method never lets a live-fetch problem fail the
     * overview itself (Constitution Principle VII).
     */
    private void refreshFromLiveQuote(TcbsStockQuoteProvider provider, InstrumentReference reference) {
        try {
            var session = referenceData.resolveSession(reference.venue(), clock.instant());
            var bar = provider.fetchCurrentBar(reference.symbol(), session.tradingDate());
            ingestion.ingestDailyBar(new IncomingDailyBar(
                    TcbsStockQuoteProvider.SOURCE, reference.symbol(), bar.tradingDate(), bar.observedAt(),
                    bar.open(), bar.high(), bar.low(), bar.close(), bar.volume(), bar.valueVnd(), "RAW", true));
        } catch (ProviderAuthenticationRequiredException e) {
            observability.recordFailure(FailureCategory.PROVIDER_AUTH_EXPIRED,
                    StockFailureReason.PROVIDER_AUTH_REQUIRED, StockOperation.SOURCE_AUTHENTICATION);
        } catch (RuntimeException e) {
            log.warn("Live TCBS quote refresh failed for {}: {}", reference.symbol(), e.getClass().getSimpleName());
            observability.recordFailure(FailureCategory.PROVIDER_UNAVAILABLE,
                    StockFailureReason.PROVIDER_CONNECTIVITY_FAILED, StockOperation.SOURCE_CONNECTIVITY);
        }
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
