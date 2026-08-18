package com.minhnb.finvera_be.market.provider.tcbs;

import com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator;
import com.minhnb.finvera_be.market.domain.breadth.BreadthUniversePolicy.InstrumentType;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.AdjustmentStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Maps documented TCBS Ouranos C001 equity facts for breadth calculation.
 * C001 has event time but no documented ordering or correction identity.
 */
public final class TcbsOuranosC001Mapper {
    private static final String C001_TIMEFRAME_SECONDS = "60";

    public TimestampedBreadthObservation map(EquityContext context, C001Message message) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(message, "message");
        if (!context.symbol().equals(message.symbol())) {
            throw new IllegalArgumentException("TCBS_OURANOS_SYMBOL_MISMATCH");
        }
        if (!C001_TIMEFRAME_SECONDS.equals(message.unitTimeFrame())) {
            throw new IllegalArgumentException("TCBS_OURANOS_TIMEFRAME_UNSUPPORTED");
        }
        return new TimestampedBreadthObservation(
                epochSeconds(message.timeSec()),
                new BreadthCalculator.SecurityInput(
                        context.venue(), context.symbol(), context.isin(), context.active(), context.vn30Member(),
                        context.instrumentType(), decimal(message.closePrice()), decimal(message.reference()),
                        AdjustmentStatus.RAW),
                nonNegativeLong(message.totalTrading()), decimal(message.totalTradingValue()),
                List.of("TCBS_OURANOS_ORDERING_UNAVAILABLE", "TCBS_OURANOS_CORRECTION_UNAVAILABLE"));
    }

    private static Instant epochSeconds(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new IllegalArgumentException("TCBS_OURANOS_TIMESTAMP_INVALID");
            return Instant.ofEpochSecond(parsed);
        } catch (NumberFormatException | java.time.DateTimeException exception) {
            throw new IllegalArgumentException("TCBS_OURANOS_TIMESTAMP_INVALID", exception);
        }
    }

    private static BigDecimal decimal(String value) {
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.signum() < 0) throw new IllegalArgumentException("TCBS_OURANOS_DECIMAL_INVALID");
            return parsed.setScale(6, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("TCBS_OURANOS_DECIMAL_INVALID", exception);
        }
    }

    private static long nonNegativeLong(String value) {
        try {
            long parsed = new BigDecimal(value).longValueExact();
            if (parsed < 0) throw new IllegalArgumentException("TCBS_OURANOS_VOLUME_INVALID");
            return parsed;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("TCBS_OURANOS_VOLUME_INVALID", exception);
        }
    }

    public record EquityContext(
            Venue venue, String symbol, String isin, boolean active, boolean vn30Member, InstrumentType instrumentType) {
        public EquityContext {
            Objects.requireNonNull(venue, "venue");
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(instrumentType, "instrumentType");
            if (!symbol.matches("[A-Z0-9]{1,32}")) throw new IllegalArgumentException("TCBS_OURANOS_SYMBOL_INVALID");
        }
    }

    public record C001Message(
            String symbol, String closePrice, String reference, String totalTrading,
            String totalTradingValue, String timeSec, String unitTimeFrame) { }

    public record TimestampedBreadthObservation(
            Instant observedAt, BreadthCalculator.SecurityInput breadthInput, Long cumulativeVolume,
            BigDecimal cumulativeValueVnd, List<String> reasonCodes) {
        public TimestampedBreadthObservation {
            Objects.requireNonNull(observedAt, "observedAt");
            Objects.requireNonNull(breadthInput, "breadthInput");
            reasonCodes = List.copyOf(reasonCodes);
        }
    }
}
