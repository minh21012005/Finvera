package com.minhnb.finvera_be.market.domain.index;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Direction;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record IndexOverview(
        LocalDate tradingDate,
        Instant observedAt,
        SessionState sessionState,
        DataStatus dataStatus,
        long revision,
        String source,
        List<IndexFact> indices) {

    public IndexOverview {
        Objects.requireNonNull(tradingDate, "tradingDate");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(sessionState, "sessionState");
        Objects.requireNonNull(dataStatus, "dataStatus");
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        indices = List.copyOf(indices);
    }

    public record IndexFact(
            IndexCode code,
            Venue venue,
            BigDecimal level,
            BigDecimal absoluteChange,
            BigDecimal percentageChange,
            Long matchedVolume,
            BigDecimal matchedValueVnd,
            Direction direction,
            DataStatus dataStatus,
            List<String> reasonCodes) {

        public IndexFact {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(venue, "venue");
            Objects.requireNonNull(dataStatus, "dataStatus");
            reasonCodes = List.copyOf(reasonCodes);
            if (level != null && level.signum() < 0) {
                throw new IllegalArgumentException("level must not be negative");
            }
            if (matchedVolume != null && matchedVolume < 0) {
                throw new IllegalArgumentException("matchedVolume must not be negative");
            }
            if (matchedValueVnd != null && matchedValueVnd.signum() < 0) {
                throw new IllegalArgumentException("matchedValueVnd must not be negative");
            }
        }
    }
}
