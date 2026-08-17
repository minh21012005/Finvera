package com.minhnb.finvera_be.market.domain.time;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/** Resolves a market session from accepted, versioned calendar data. */
public final class MarketTimePolicy {

    private final ZoneId marketZone;

    public MarketTimePolicy(ZoneId marketZone) {
        this.marketZone = Objects.requireNonNull(marketZone, "marketZone");
    }

    public SessionState sessionAt(
            Instant at,
            CalendarDay calendarDay,
            List<SessionWindow> sessionWindows) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(calendarDay, "calendarDay");
        Objects.requireNonNull(sessionWindows, "sessionWindows");

        var localDateTime = at.atZone(marketZone).toLocalDateTime();
        if (!calendarDay.tradingDate().equals(localDateTime.toLocalDate())) {
            return SessionState.UNKNOWN;
        }
        if (!calendarDay.tradingDay()) {
            return SessionState.NON_TRADING_DAY;
        }

        LocalDate date = localDateTime.toLocalDate();
        LocalTime time = localDateTime.toLocalTime();
        return sessionWindows.stream()
                .filter(window -> window.venue() == calendarDay.venue())
                .filter(window -> window.appliesOn(date))
                .filter(window -> window.contains(time))
                .map(SessionWindow::state)
                .findFirst()
                .orElse(SessionState.UNKNOWN);
    }

    public record CalendarDay(
            Venue venue,
            LocalDate tradingDate,
            boolean tradingDay,
            String reasonCode,
            String policyVersion) {

        public CalendarDay {
            Objects.requireNonNull(venue, "venue");
            Objects.requireNonNull(tradingDate, "tradingDate");
            requireText(reasonCode, "reasonCode");
            requireText(policyVersion, "policyVersion");
        }
    }

    public record SessionWindow(
            Venue venue,
            SessionState state,
            LocalTime startsAt,
            LocalTime endsAt,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String policyVersion) {

        public SessionWindow {
            Objects.requireNonNull(venue, "venue");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(startsAt, "startsAt");
            Objects.requireNonNull(endsAt, "endsAt");
            Objects.requireNonNull(effectiveFrom, "effectiveFrom");
            requireText(policyVersion, "policyVersion");
            if (!endsAt.isAfter(startsAt)) {
                throw new IllegalArgumentException("endsAt must be after startsAt");
            }
            if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
                throw new IllegalArgumentException("effectiveTo must not precede effectiveFrom");
            }
        }

        private boolean appliesOn(LocalDate date) {
            return !date.isBefore(effectiveFrom)
                    && (effectiveTo == null || !date.isAfter(effectiveTo));
        }

        private boolean contains(LocalTime time) {
            return !time.isBefore(startsAt) && time.isBefore(endsAt);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
