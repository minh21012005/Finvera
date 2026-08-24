package com.minhnb.finvera_be.market.provider.tcbs;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Pure parser for the official TCBS Thesis price-board text-frame contract. */
public final class TcbsThesisFrameMapper {

    private final JsonMapper json = JsonMapper.builder().build();

    public Event map(String raw, Instant receivedAt) {
        requireText(raw, "raw frame");
        Objects.requireNonNull(receivedAt, "receivedAt");
        if (raw.startsWith("d|")) {
            return new ControlFrame(raw.startsWith("d|0|"), receivedAt);
        }
        String[] parts = raw.split("\\|", 3);
        if (parts.length != 3 || !parts[0].equals("s")) {
            throw new IllegalArgumentException("Unsupported TCBS Thesis frame envelope");
        }
        JsonNode payload;
        try {
            payload = json.readTree(parts[2]);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid TCBS Thesis JSON payload", exception);
        }
        return switch (parts[1]) {
            case "8" -> index(payload, receivedAt);
            case "4" -> reference(payload, receivedAt);
            case "6" -> trade(payload, receivedAt);
            default -> new UnsupportedDataFrame(parts[1], receivedAt);
        };
    }

    private static IndexUpdate index(JsonNode node, Instant receivedAt) {
        int indexNumber = integer(node, "indexNumber", true);
        IndexCode code = switch (indexNumber) {
            case 1 -> IndexCode.VN_INDEX;
            case 2 -> IndexCode.VN30;
            case 3 -> IndexCode.HNX_INDEX;
            case 5 -> IndexCode.UPCOM_INDEX;
            default -> throw new IllegalArgumentException("Unsupported TCBS indexNumber: " + indexNumber);
        };
        BigDecimal level = decimal(node, "index", true);
        BigDecimal change = decimal(node, "change", true);
        if (level.signum() < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        BigDecimal reference = level.subtract(change);
        if (reference.signum() <= 0) {
            throw new IllegalArgumentException("derived reference index must be positive");
        }
        Long volume = longValue(node, "volume", false);
        BigDecimal value = decimal(node, "value", false);
        validateNonNegative(volume, "volume");
        validateNonNegative(value, "value");
        BreadthCounts breadth = breadth(node);
        String session = text(node, "session", false);
        return new IndexUpdate(indexNumber, code, level, reference, change,
                decimal(node, "changePercent", false), volume, value, breadth,
                session, receivedAt);
    }

    private static BreadthCounts breadth(JsonNode node) {
        Integer advancing = nullableInteger(node, "increase");
        Integer declining = nullableInteger(node, "decrease");
        Integer unchanged = nullableInteger(node, "notChange");
        if (advancing == null && declining == null && unchanged == null) {
            return null;
        }
        if (advancing == null || declining == null || unchanged == null) {
            throw new IllegalArgumentException("TCBS breadth counters must be jointly present");
        }
        return new BreadthCounts(advancing, declining, unchanged);
    }

    private static EquityReferenceUpdate reference(JsonNode node, Instant receivedAt) {
        String symbol = symbol(node);
        BigDecimal reference = decimal(node, "refPrice", true);
        if (reference.signum() <= 0) {
            throw new IllegalArgumentException("refPrice must be positive");
        }
        return new EquityReferenceUpdate(symbol, reference, receivedAt);
    }

    private static EquityTradeUpdate trade(JsonNode node, Instant receivedAt) {
        String symbol = symbol(node);
        BigDecimal matchPrice = decimal(node, "matchPrice", true);
        if (matchPrice.signum() < 0) {
            throw new IllegalArgumentException("matchPrice must be non-negative");
        }
        Long totalVolume = longValue(node, "totalVolume", false);
        BigDecimal totalValue = decimal(node, "totalValue", false);
        validateNonNegative(totalVolume, "totalVolume");
        validateNonNegative(totalValue, "totalValue");
        return new EquityTradeUpdate(symbol, matchPrice,
                decimal(node, "change", false), decimal(node, "changePercent", false),
                totalVolume, totalValue, receivedAt);
    }

    private static String symbol(JsonNode node) {
        String symbol = text(node, "symbol", true).toUpperCase(Locale.ROOT);
        if (!symbol.matches("[A-Z0-9]{1,10}")) {
            throw new IllegalArgumentException("symbol is invalid");
        }
        return symbol;
    }

    private static BigDecimal decimal(JsonNode node, String field, boolean required) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            if (required) throw new IllegalArgumentException(field + " is required");
            return null;
        }
        try {
            if (value.isNumber()) return value.decimalValue();
            if (value.isTextual() && !value.stringValue().isBlank()) return new BigDecimal(value.stringValue());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be decimal", exception);
        }
        throw new IllegalArgumentException(field + " must be decimal");
    }

    private static Long longValue(JsonNode node, String field, boolean required) {
        BigDecimal value = decimal(node, field, required);
        if (value == null) return null;
        try {
            return value.longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + " must be an exact long", exception);
        }
    }

    private static int integer(JsonNode node, String field, boolean required) {
        Long value = longValue(node, field, required);
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return Math.toIntExact(value);
    }

    private static Integer nullableInteger(JsonNode node, String field) {
        Long value = longValue(node, field, false);
        if (value == null) return null;
        int result = Math.toIntExact(value);
        if (result < 0) throw new IllegalArgumentException(field + " must be non-negative");
        return result;
    }

    private static String text(JsonNode node, String field, boolean required) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            if (required) throw new IllegalArgumentException(field + " is required");
            return null;
        }
        String result = value.isTextual() ? value.stringValue() : value.toString();
        if (result == null || result.isBlank()) {
            if (required) throw new IllegalArgumentException(field + " is required");
            return null;
        }
        return result;
    }

    private static void validateNonNegative(Number value, String field) {
        if (value != null && value.longValue() < 0) throw new IllegalArgumentException(field + " must be non-negative");
    }

    private static void validateNonNegative(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) throw new IllegalArgumentException(field + " must be non-negative");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }

    public sealed interface Event permits ControlFrame, UnsupportedDataFrame, IndexUpdate,
            EquityReferenceUpdate, EquityTradeUpdate {
        Instant receivedAt();
    }

    public record ControlFrame(boolean authenticationResponse, Instant receivedAt) implements Event { }
    public record UnsupportedDataFrame(String code, Instant receivedAt) implements Event { }
    public record BreadthCounts(int advancing, int declining, int unchanged) { }
    public record IndexUpdate(int indexNumber, IndexCode code, BigDecimal level, BigDecimal referenceLevel,
            BigDecimal absoluteChange, BigDecimal percentageChange, Long matchedVolume,
            BigDecimal matchedValueVnd, BreadthCounts breadth, String rawProviderSession,
            Instant receivedAt) implements Event { }
    public record EquityReferenceUpdate(String symbol, BigDecimal referencePrice,
            Instant receivedAt) implements Event { }
    public record EquityTradeUpdate(String symbol, BigDecimal matchPrice, BigDecimal absoluteChange,
            BigDecimal percentageChange, Long totalVolume, BigDecimal totalValueVnd,
            Instant receivedAt) implements Event { }
}
