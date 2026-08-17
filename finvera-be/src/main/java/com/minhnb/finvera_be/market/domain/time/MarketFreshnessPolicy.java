package com.minhnb.finvera_be.market.domain.time;

import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.CURRENT;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.DELAYED;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.STALE;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.UNAVAILABLE;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Evaluates transport freshness independently from provider data quality. */
public final class MarketFreshnessPolicy {

    private static final Duration CURRENT_GRACE = Duration.ofSeconds(30);
    private static final Duration DELAYED_WINDOW = Duration.ofMinutes(5);

    public DataStatus evaluate(
            Instant observedAt,
            Instant evaluatedAt,
            Duration contractedDelay,
            SessionState sessionState,
            DataStatus providerQuality) {
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(contractedDelay, "contractedDelay");
        Objects.requireNonNull(sessionState, "sessionState");
        Objects.requireNonNull(providerQuality, "providerQuality");
        if (contractedDelay.isNegative()) {
            throw new IllegalArgumentException("contractedDelay must not be negative");
        }
        if (evaluatedAt.isBefore(observedAt)) {
            throw new IllegalArgumentException("evaluatedAt must not precede observedAt");
        }

        if (!sessionState.isActive()) {
            return providerQuality;
        }

        Duration ageAfterContractedDelay = Duration.between(
                observedAt.plus(contractedDelay), evaluatedAt);
        DataStatus freshness;
        if (ageAfterContractedDelay.compareTo(CURRENT_GRACE) <= 0) {
            freshness = CURRENT;
        } else if (ageAfterContractedDelay.compareTo(DELAYED_WINDOW) <= 0) {
            freshness = DELAYED;
        } else {
            freshness = STALE;
        }
        return DataStatus.mostActionable(freshness, providerQuality);
    }

    public DataStatus evaluateMissing() {
        return UNAVAILABLE;
    }
}
