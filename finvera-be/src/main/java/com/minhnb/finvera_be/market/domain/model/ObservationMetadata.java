package com.minhnb.finvera_be.market.domain.model;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.AdjustmentStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Objects;

public record ObservationMetadata(
        String source,
        Instant observedAt,
        Instant effectiveAt,
        Instant ingestedAt,
        ZoneId marketTimezone,
        Currency currency,
        Venue venue,
        AdjustmentStatus adjustmentStatus) {

    public static final ZoneId VIETNAM_MARKET_TIME = ZoneId.of("Asia/Ho_Chi_Minh");

    public ObservationMetadata {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        Objects.requireNonNull(marketTimezone, "marketTimezone");
        Objects.requireNonNull(venue, "venue");
        Objects.requireNonNull(adjustmentStatus, "adjustmentStatus");
        if (source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        if (ingestedAt.isBefore(observedAt)) {
            throw new IllegalArgumentException("ingestedAt must not precede observedAt");
        }
    }
}
