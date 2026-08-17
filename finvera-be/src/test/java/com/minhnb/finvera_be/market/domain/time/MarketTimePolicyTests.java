package com.minhnb.finvera_be.market.domain.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketTimePolicyTests {

    private static final Instant OBSERVED = Instant.parse("2026-08-17T03:00:00Z");
    private static final Duration CONTRACTED_DELAY = Duration.ofSeconds(15);

    @Test
    void freshnessUsesExactCurrentAndDelayedBoundaries() {
        var policy = new MarketFreshnessPolicy();

        assertThat(policy.evaluate(
                OBSERVED, OBSERVED.plus(CONTRACTED_DELAY).plusSeconds(30),
                CONTRACTED_DELAY, SessionState.OPEN, DataStatus.CURRENT))
                .isEqualTo(DataStatus.CURRENT);
        assertThat(policy.evaluate(
                OBSERVED, OBSERVED.plus(CONTRACTED_DELAY).plusSeconds(31),
                CONTRACTED_DELAY, SessionState.OPEN, DataStatus.CURRENT))
                .isEqualTo(DataStatus.DELAYED);
        assertThat(policy.evaluate(
                OBSERVED, OBSERVED.plus(CONTRACTED_DELAY).plus(Duration.ofMinutes(5)),
                CONTRACTED_DELAY, SessionState.OPEN, DataStatus.CURRENT))
                .isEqualTo(DataStatus.DELAYED);
        assertThat(policy.evaluate(
                OBSERVED, OBSERVED.plus(CONTRACTED_DELAY).plus(Duration.ofMinutes(5)).plusSeconds(1),
                CONTRACTED_DELAY, SessionState.OPEN, DataStatus.CURRENT))
                .isEqualTo(DataStatus.STALE);
    }

    @Test
    void closedAndNonTradingSessionsDoNotBecomeStaleFromWallClockAge() {
        var policy = new MarketFreshnessPolicy();
        Instant muchLater = OBSERVED.plus(Duration.ofDays(3));

        assertThat(policy.evaluate(
                OBSERVED, muchLater, CONTRACTED_DELAY, SessionState.CLOSED, DataStatus.CURRENT))
                .isEqualTo(DataStatus.CURRENT);
        assertThat(policy.evaluate(
                OBSERVED, muchLater, CONTRACTED_DELAY, SessionState.NON_TRADING_DAY, DataStatus.CURRENT))
                .isEqualTo(DataStatus.CURRENT);
    }

    @Test
    void qualityAndFreshnessRemainDistinctBeforeMostActionableStatusIsChosen() {
        var policy = new MarketFreshnessPolicy();

        assertThat(policy.evaluate(
                OBSERVED, OBSERVED.plus(Duration.ofHours(1)), CONTRACTED_DELAY,
                SessionState.OPEN, DataStatus.PARTIAL))
                .isEqualTo(DataStatus.PARTIAL);
        assertThat(policy.evaluateMissing()).isEqualTo(DataStatus.UNAVAILABLE);
    }

    @Test
    void calendarAndVersionedWindowsUseVietnamTimeRegardlessOfHostTimezone() {
        var policy = new MarketTimePolicy(ZoneId.of("Asia/Ho_Chi_Minh"));
        var day = new MarketTimePolicy.CalendarDay(
                Venue.HOSE, LocalDate.of(2026, 8, 17), true, "NORMAL", "vn-calendar-2026-v1");
        var windows = List.of(new MarketTimePolicy.SessionWindow(
                Venue.HOSE,
                SessionState.OPEN,
                LocalTime.of(9, 0),
                LocalTime.of(11, 30),
                LocalDate.of(2026, 1, 1),
                null,
                "hose-session-v1"));

        assertThat(policy.sessionAt(Instant.parse("2026-08-17T02:00:00Z"), day, windows))
                .isEqualTo(SessionState.OPEN);
        assertThat(policy.sessionAt(Instant.parse("2026-08-17T05:00:00Z"), day, windows))
                .isEqualTo(SessionState.UNKNOWN);
    }

    @Test
    void acceptedCalendarOverridesWeekdayAssumptions() {
        var policy = new MarketTimePolicy(ZoneId.of("Asia/Ho_Chi_Minh"));
        var holiday = new MarketTimePolicy.CalendarDay(
                Venue.HNX, LocalDate.of(2026, 8, 17), false, "HOLIDAY", "vn-calendar-2026-v1");

        assertThat(policy.sessionAt(OBSERVED, holiday, List.of()))
                .isEqualTo(SessionState.NON_TRADING_DAY);
    }
}
