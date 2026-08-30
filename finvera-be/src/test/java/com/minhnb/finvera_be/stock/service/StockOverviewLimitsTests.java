package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.stock.dto.StockOverviewResponse;
import com.minhnb.finvera_be.stock.domain.overview.StockOverviewCalculator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Feature 008 US3 (FR-003): session price limits and the textual at-limit cue. */
class StockOverviewLimitsTests {

    @Test
    void limitStateIsAtCeilingOrAtFloorOnlyWhenTheLastPriceEqualsTheLimit() {
        assertThat(StockOverviewService.limitState(new BigDecimal("36900"), new BigDecimal("36900"), new BigDecimal("32100")))
                .isEqualTo("AT_CEILING");
        assertThat(StockOverviewService.limitState(new BigDecimal("32100.000000"), new BigDecimal("36900"), new BigDecimal("32100")))
                .isEqualTo("AT_FLOOR");
        assertThat(StockOverviewService.limitState(new BigDecimal("35000"), new BigDecimal("36900"), new BigDecimal("32100")))
                .isNull();
        assertThat(StockOverviewService.limitState(null, new BigDecimal("36900"), new BigDecimal("32100"))).isNull();
        assertThat(StockOverviewService.limitState(new BigDecimal("35000"), null, null)).isNull();
    }

    @Test
    void responseCarriesLimitsAndRoomAsNullableFieldsNeverZero() {
        var price = new StockOverviewCalculator().calculate(new StockOverviewCalculator.Input(
                new BigDecimal("36900"), new BigDecimal("34500"), 1_000L, new BigDecimal("36900000"), 1_000_000L));
        var withLimits = StockOverviewResponse.PriceResponse.from(price,
                new StockOverviewService.SessionLimits(new BigDecimal("36900"), new BigDecimal("32100"), 12_345L, "AT_CEILING"));
        assertThat(withLimits.ceilingPrice()).isEqualTo("36900");
        assertThat(withLimits.floorPrice()).isEqualTo("32100");
        assertThat(withLimits.foreignRoom()).isEqualTo(12_345L);
        assertThat(withLimits.limitState()).isEqualTo("AT_CEILING");

        var without = StockOverviewResponse.PriceResponse.from(price, StockOverviewService.SessionLimits.UNAVAILABLE);
        assertThat(without.ceilingPrice()).isNull();
        assertThat(without.floorPrice()).isNull();
        assertThat(without.foreignRoom()).isNull();
        assertThat(without.limitState()).isNull();
        assertThat(List.of(without.last())).containsExactly("36900");
    }
}
