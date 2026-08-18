package com.minhnb.finvera_be.stock.domain.overview;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.Direction;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * FR-001, FR-002, FR-013: assembles the overview price block from accepted
 * facts only. {@code priceApplicability} describes the change-basis fields
 * ({@code absoluteChange}/{@code percentageChange}/{@code direction}), not
 * {@code lastPrice}/{@code volume}/{@code valueVnd} individually — the last
 * price can be fully present while the change basis is unavailable (FR-002),
 * and the two must stay distinguishable rather than collapsed into one flag.
 */
public final class StockOverviewCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public StockOverviewResult calculate(Input input) {
        Objects.requireNonNull(input, "input");

        if (input.lastPrice() == null) {
            return new StockOverviewResult(MetricApplicability.MISSING, null, null, null, null,
                    Direction.UNCHANGED, input.volume(), input.valueVnd(), null, "PRICE_UNAVAILABLE");
        }

        BigDecimal marketCap = (input.sharesOutstanding() == null)
                ? null
                : input.lastPrice().multiply(BigDecimal.valueOf(input.sharesOutstanding()))
                        .setScale(6, RoundingMode.UNNECESSARY);

        if (input.referencePrice() == null) {
            return new StockOverviewResult(MetricApplicability.MISSING, input.lastPrice(), null, null, null,
                    Direction.UNCHANGED, input.volume(), input.valueVnd(), marketCap,
                    "REFERENCE_PRICE_UNAVAILABLE");
        }

        BigDecimal absoluteChange = input.lastPrice().subtract(input.referencePrice());
        BigDecimal percentageChange = input.referencePrice().signum() == 0
                ? null
                : absoluteChange.multiply(ONE_HUNDRED).divide(input.referencePrice(), 6, RoundingMode.HALF_UP);
        Direction direction = switch (absoluteChange.signum()) {
            case 1 -> Direction.UP;
            case -1 -> Direction.DOWN;
            default -> Direction.UNCHANGED;
        };
        MetricApplicability applicability = percentageChange == null
                ? MetricApplicability.MISSING
                : MetricApplicability.DEFINED;
        String reason = percentageChange == null ? "REFERENCE_PRICE_INVALID" : null;

        return new StockOverviewResult(applicability, input.lastPrice(), input.referencePrice(), absoluteChange,
                percentageChange, direction, input.volume(), input.valueVnd(), marketCap, reason);
    }

    public record Input(
            BigDecimal lastPrice,
            BigDecimal referencePrice,
            Long volume,
            BigDecimal valueVnd,
            Long sharesOutstanding) {
    }

    public record StockOverviewResult(
            MetricApplicability priceApplicability,
            BigDecimal lastPrice,
            BigDecimal referencePrice,
            BigDecimal absoluteChange,
            BigDecimal percentageChange,
            Direction direction,
            Long volume,
            BigDecimal valueVnd,
            BigDecimal marketCapVnd,
            String changeBasisReason) {
    }
}
