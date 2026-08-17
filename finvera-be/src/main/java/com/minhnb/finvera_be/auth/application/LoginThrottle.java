package com.minhnb.finvera_be.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

public final class LoginThrottle {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Deque<Instant> failures = new ArrayDeque<>();

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    public synchronized void checkAllowed() {
        removeExpired();
        if (failures.size() >= MAX_FAILURES) {
            throw new LoginRateLimitedException();
        }
    }

    public synchronized void recordFailure() {
        removeExpired();
        failures.addLast(clock.instant());
    }

    public synchronized void reset() {
        failures.clear();
    }

    private void removeExpired() {
        var cutoff = clock.instant().minus(WINDOW);
        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.removeFirst();
        }
    }

    public static final class LoginRateLimitedException extends RuntimeException {
        public LoginRateLimitedException() {
            super("Login is temporarily rate limited");
        }
    }
}
