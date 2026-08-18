package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import com.minhnb.finvera_be.market.domain.time.MarketTimePolicy;
import com.minhnb.finvera_be.market.domain.time.MarketTimePolicy.CalendarDay;
import com.minhnb.finvera_be.market.domain.time.MarketTimePolicy.SessionWindow;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketCalendarDayRepository;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.market.repository.MarketSessionWindowRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

@Service
public class DefaultMarketReferenceDataService implements MarketReferenceDataService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MarketInstrumentRepository instruments;
    private final MarketCalendarDayRepository calendarDays;
    private final MarketSessionWindowRepository sessionWindows;
    private final MarketTimePolicy timePolicy;

    public DefaultMarketReferenceDataService(
            MarketInstrumentRepository instruments,
            MarketCalendarDayRepository calendarDays,
            MarketSessionWindowRepository sessionWindows) {
        this.instruments = instruments;
        this.calendarDays = calendarDays;
        this.sessionWindows = sessionWindows;
        this.timePolicy = new MarketTimePolicy(MARKET_ZONE);
    }

    @Override
    public Optional<InstrumentReference> findActiveInstrumentBySymbol(String symbol) {
        Objects.requireNonNull(symbol, "symbol");
        return instruments.findFirstBySymbolAndListedToIsNull(symbol.toUpperCase())
                .map(DefaultMarketReferenceDataService::toReference);
    }

    @Override
    public List<InstrumentReference> searchActiveInstrumentsBySymbolPrefix(String symbolPrefix, int limit) {
        Objects.requireNonNull(symbolPrefix, "symbolPrefix");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return instruments
                .findByListedToIsNullAndSymbolStartingWithIgnoreCaseOrderBySymbolAsc(
                        symbolPrefix, Limit.of(limit))
                .stream()
                .map(DefaultMarketReferenceDataService::toReference)
                .toList();
    }

    @Override
    public SessionContext resolveSession(String venue, Instant at) {
        Objects.requireNonNull(venue, "venue");
        Objects.requireNonNull(at, "at");
        var tradingDate = at.atZone(MARKET_ZONE).toLocalDate();
        var calendarDay = calendarDays.findFirstByVenueAndTradingDateOrderByAcceptedAtDesc(venue, tradingDate)
                .map(entity -> new CalendarDay(
                        Venue.valueOf(entity.getVenue()),
                        entity.getTradingDate(),
                        entity.isTradingDay(),
                        entity.getReasonCode(),
                        entity.getPolicyVersion()))
                .orElse(null);
        if (calendarDay == null) {
            return new SessionContext(SessionState.UNKNOWN, tradingDate);
        }
        List<SessionWindow> windows = sessionWindows.findByVenue(venue).stream()
                .map(entity -> new SessionWindow(
                        Venue.valueOf(entity.getVenue()),
                        SessionState.valueOf(entity.getState()),
                        entity.getStartLocal(),
                        entity.getEndLocal(),
                        entity.getEffectiveFrom(),
                        entity.getEffectiveTo(),
                        entity.getPolicyVersion()))
                .toList();
        SessionState state = timePolicy.sessionAt(at, calendarDay, windows);
        return new SessionContext(state, tradingDate);
    }

    private static InstrumentReference toReference(MarketInstrumentEntity entity) {
        return new InstrumentReference(
                entity.getId(), entity.getVenue(), entity.getSymbol(),
                entity.getInstrumentType(), entity.getStatus());
    }
}
