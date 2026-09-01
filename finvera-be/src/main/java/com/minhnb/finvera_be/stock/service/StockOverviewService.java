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
import com.minhnb.finvera_be.stock.provider.StockQuoteProvider;
import com.minhnb.finvera_be.stock.provider.StockQuoteProvider.QuoteObservation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-001, FR-002, FR-013; DATA-001, DATA-002, DATA-005, NFR-003. Assembles one
 * coherent overview read model from accepted facts only. "Current price" is
 * the latest accepted TCBS Thesis quote when available, otherwise the latest
 * accepted daily bar persisted in PostgreSQL. Provider calls are not made from
 * this read path: both the live overlay and Vnstock/KBS history enter through
 * explicit ingestion boundaries.
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
    private final Optional<StockQuoteProvider> liveQuotes;

    @Autowired
    public StockOverviewService(
            MarketReferenceDataService referenceData,
            EquityProfileRepository profiles,
            EquityDailyBarRepository dailyBars,
            SectorReferenceRepository sectors,
            Clock clock,
            Optional<StockQuoteProvider> liveQuotes) {
        this.referenceData = referenceData;
        this.profiles = profiles;
        this.dailyBars = dailyBars;
        this.sectors = sectors;
        this.clock = clock;
        this.liveQuotes = liveQuotes;
    }

    /** Test/fixture convenience: no live provider. */
    public StockOverviewService(MarketReferenceDataService referenceData, EquityProfileRepository profiles,
            EquityDailyBarRepository dailyBars, SectorReferenceRepository sectors, Clock clock) {
        this(referenceData, profiles, dailyBars, sectors, clock, Optional.empty());
    }

    @Transactional
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

        Optional<QuoteObservation> liveQuote = liveQuotes.flatMap(provider -> {
            try {
                return Optional.of(provider.getQuote(reference.symbol()));
            } catch (RuntimeException unavailable) {
                return Optional.empty();
            }
        });

        BigDecimal openPrice = liveQuote.map(QuoteObservation::openPrice)
                .orElseGet(() -> latestBar.map(EquityDailyBarEntity::getOpenPrice).orElse(null));
        BigDecimal highPrice = liveQuote.map(QuoteObservation::highPrice)
                .orElseGet(() -> latestBar.map(EquityDailyBarEntity::getHighPrice).orElse(null));
        BigDecimal lowPrice = liveQuote.map(QuoteObservation::lowPrice)
                .orElseGet(() -> latestBar.map(EquityDailyBarEntity::getLowPrice).orElse(null));

        StockOverviewResult price = calculator.calculate(new StockOverviewCalculator.Input(
                liveQuote.map(QuoteObservation::lastPrice)
                        .orElseGet(() -> latestBar.map(EquityDailyBarEntity::getClosePrice).orElse(null)),
                liveQuote.map(QuoteObservation::officialReferencePrice)
                        .orElseGet(() -> latestBar.map(EquityDailyBarEntity::getReferencePrice)
                                .orElseGet(() -> previousBar.map(EquityDailyBarEntity::getClosePrice).orElse(null))),
                openPrice,
                highPrice,
                lowPrice,
                liveQuote.map(QuoteObservation::sessionVolume)
                        .orElseGet(() -> latestBar.map(EquityDailyBarEntity::getVolume).orElse(null)),
                liveQuote.map(QuoteObservation::sessionValueVnd)
                        .orElseGet(() -> latestBar.map(EquityDailyBarEntity::getValueVnd).orElse(null)),
                profile.map(EquityProfileEntity::getSharesOutstanding).orElse(null)));

        Instant now = clock.instant();
        var session = referenceData.resolveSession(reference.venue(), now);
        Instant asOf = liveQuote.map(QuoteObservation::observedAt).orElse(now);
        DataStatus dataStatus = liveQuote.isPresent() ? DataStatus.CURRENT
                : evaluateOverviewFreshness(reference.venue(), latestBar, session.tradingDate());

        List<String> reasonCodes = new ArrayList<>();
        if (profile.isEmpty()) {
            reasonCodes.add("PROFILE_UNAVAILABLE");
        }
        if (latestBar.isEmpty()) {
            reasonCodes.add("PRICE_UNAVAILABLE");
        }
        if (liveQuote.isPresent()) {
            reasonCodes.remove("PRICE_UNAVAILABLE");
            reasonCodes.add("TCBS_THESIS_LIVE_QUOTE");
        }
        if (price.changeBasisReason() != null) {
            reasonCodes.add(price.changeBasisReason());
        }
        // Feature 008 US3: session limits and foreign room are live-overlay session context.
        SessionLimits limits = liveQuote
                .map(q -> new SessionLimits(q.ceilingPrice(), q.floorPrice(), q.foreignRoom(),
                        limitState(q.lastPrice(), q.ceilingPrice(), q.floorPrice())))
                .orElse(SessionLimits.UNAVAILABLE);
        if (limits.ceilingPrice() == null && limits.floorPrice() == null) {
            reasonCodes.add("PRICE_LIMITS_UNAVAILABLE");
        }

        String coherenceKey = CoherenceKeys.of(List.of(
                profile.map(p -> p.getId().toString()).orElse(""),
                latestBar.map(b -> b.getId() + ":" + b.getRevision()).orElse(""),
                previousBar.map(b -> b.getId() + ":" + b.getRevision()).orElse(""),
                liveQuote.map(q -> q.symbol() + ":" + q.observedAt()).orElse("")));

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
                liveQuote.isPresent() ? session.tradingDate()
                        : latestBar.map(EquityDailyBarEntity::getTradingDate).orElse(session.tradingDate()),
                asOf, dataStatus, List.copyOf(reasonCodes), coherenceKey, limits));
    }

    /** Textual at-limit cue (never colour-only): last price equal to the ceiling or floor. */
    static String limitState(BigDecimal last, BigDecimal ceiling, BigDecimal floor) {
        if (last == null) {
            return null;
        }
        if (ceiling != null && last.compareTo(ceiling) == 0) {
            return "AT_CEILING";
        }
        if (floor != null && last.compareTo(floor) == 0) {
            return "AT_FLOOR";
        }
        return null;
    }

    public record SessionLimits(BigDecimal ceilingPrice, BigDecimal floorPrice, Long foreignRoom, String limitState) {
        public static final SessionLimits UNAVAILABLE = new SessionLimits(null, null, null, null);
    }

    private DataStatus evaluateOverviewFreshness(String venue, Optional<EquityDailyBarEntity> latestBar, LocalDate asOfTradingDate) {
        if (latestBar.isEmpty()) {
            return freshnessPolicy.evaluateMissing();
        }
        int sessionsBehind = referenceData.countTradingSessionsBetween(venue, latestBar.orElseThrow().getTradingDate(), asOfTradingDate);
        return freshnessPolicy.evaluateDailyBarSeries(sessionsBehind);
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
            String coherenceKey,
            SessionLimits limits) {
        /** Compatibility for callers/tests that predate Feature 008's session limits. */
        public StockOverview(String symbol, String venue, String companyNameVi, String companyNameEn,
                String listingStatus, String sector, String sectorScheme, Long sharesOutstanding,
                StockOverviewResult price, SessionState sessionState, LocalDate tradingDate, Instant asOf,
                DataStatus dataStatus, List<String> reasonCodes, String coherenceKey) {
            this(symbol, venue, companyNameVi, companyNameEn, listingStatus, sector, sectorScheme,
                    sharesOutstanding, price, sessionState, tradingDate, asOf, dataStatus, reasonCodes,
                    coherenceKey, SessionLimits.UNAVAILABLE);
        }
    }
}
