package com.minhnb.finvera_be.stock.domain.overview;

import static com.minhnb.finvera_be.market.domain.model.MarketTypes.Direction.DOWN;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.Direction.UNCHANGED;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.Direction.UP;
import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.overview.StockOverviewCalculator.Input;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** FR-001, FR-002, FR-013; DATA-003, DATA-004, DATA-007. */
class StockOverviewTests {

    private final StockOverviewCalculator calculator = new StockOverviewCalculator();

    @Test
    void computesExactChangeAndDirectionFromThePreviousValidOfficialClose() {
        var result = calculator.calculate(new Input(
                new BigDecimal("123600.000000"), new BigDecimal("122500.000000"), 2_270_000L,
                new BigDecimal("280457200000.0000"), 1_462_000_000L));

        assertThat(result.priceApplicability()).isEqualTo(MetricApplicability.DEFINED);
        assertThat(result.absoluteChange()).isEqualByComparingTo("1100.000000");
        assertThat(result.percentageChange()).isEqualByComparingTo("0.897959");
        assertThat(result.direction()).isEqualTo(UP);
        assertThat(result.changeBasisReason()).isNull();
        assertThat(result.marketCapVnd()).isEqualByComparingTo("180703200000000.000000");
    }

    @Test
    void reportsDownDirectionForANegativeChange() {
        var result = calculator.calculate(new Input(
                new BigDecimal("119400.000000"), new BigDecimal("122500.000000"), 1_800_000L, null, null));
        assertThat(result.direction()).isEqualTo(DOWN);
        assertThat(result.absoluteChange()).isEqualByComparingTo("-3100.000000");
    }

    @Test
    void reportsUnchangedWhenLastEqualsTheReferencePrice() {
        var result = calculator.calculate(new Input(
                new BigDecimal("98420.000000"), new BigDecimal("98420.000000"), 100_000L, null, null));
        assertThat(result.direction()).isEqualTo(UNCHANGED);
        assertThat(result.absoluteChange()).isEqualByComparingTo("0.000000");
    }

    @Test
    void changeFieldsAreUnavailableRatherThanInferredWhenTheReferenceBasisIsMissing() {
        var result = calculator.calculate(new Input(
                new BigDecimal("123600.000000"), null, 2_270_000L, null, null));

        assertThat(result.absoluteChange()).isNull();
        assertThat(result.percentageChange()).isNull();
        assertThat(result.direction()).isEqualTo(UNCHANGED);
        assertThat(result.priceApplicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(result.changeBasisReason()).isEqualTo("REFERENCE_PRICE_UNAVAILABLE");
        // The last price itself remains visible even though the change basis is missing (FR-002).
        assertThat(result.lastPrice()).isEqualByComparingTo("123600.000000");
    }

    @Test
    void everyFieldIsMissingWhenNoAcceptedPriceExistsAtAll() {
        var result = calculator.calculate(new Input(null, null, null, null, null));
        assertThat(result.priceApplicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(result.lastPrice()).isNull();
        assertThat(result.marketCapVnd()).isNull();
        assertThat(result.changeBasisReason()).isEqualTo("PRICE_UNAVAILABLE");
    }

    @Test
    void marketCapIsNullWithoutSharesOutstandingRatherThanZero() {
        var result = calculator.calculate(new Input(
                new BigDecimal("100.000000"), new BigDecimal("100.000000"), null, null, null));
        assertThat(result.marketCapVnd()).isNull();
    }
}
