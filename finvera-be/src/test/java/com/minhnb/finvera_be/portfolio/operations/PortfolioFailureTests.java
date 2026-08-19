package com.minhnb.finvera_be.portfolio.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.DrawdownPoint;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.PerformanceHistoryResult;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.PortfolioHoldingsState;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.PositionResult;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.TransactionInput;
import com.minhnb.finvera_be.portfolio.domain.model.PortfolioTypes.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortfolioFailureTests {

    private final UUID fptId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID vnmId = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    @DisplayName("DATA-004: Missing/withheld price retains quantity but yields null marketValue/unrealizedPL and false priceAvailable")
    void withheldPriceDegradesGracefully() {
        Instant t0 = Instant.parse("2026-08-01T00:00:00Z");
        List<TransactionInput> txs = List.of(
                new TransactionInput(UUID.randomUUID(), 1L, TransactionType.DEPOSIT, null, null, null, null, BigDecimal.ZERO, new BigDecimal("100000000"), "VND", t0, t0, null, null),
                new TransactionInput(UUID.randomUUID(), 2L, TransactionType.BUY, fptId, "FPT", new BigDecimal("1000"), new BigDecimal("50000"), BigDecimal.ZERO, null, "VND", t0, t0, null, null),
                new TransactionInput(UUID.randomUUID(), 3L, TransactionType.BUY, vnmId, "VNM", new BigDecimal("500"), new BigDecimal("70000"), BigDecimal.ZERO, null, "VND", t0, t0, null, null)
        );

        // FPT has a price, VNM is missing/withheld
        Map<UUID, BigDecimal> prices = Map.of(fptId, new BigDecimal("55000"));
        Map<UUID, String> symbols = Map.of(fptId, "FPT", vnmId, "VNM");

        PortfolioHoldingsState state = PortfolioAnalyticsV1.replayHoldings(txs, prices, symbols);

        PositionResult fptPos = state.positions().get(fptId);
        PositionResult vnmPos = state.positions().get(vnmId);

        assertThat(fptPos.priceAvailable()).isTrue();
        assertThat(fptPos.marketValue()).isEqualByComparingTo("55000000");

        assertThat(vnmPos.priceAvailable()).isFalse();
        assertThat(vnmPos.marketValue()).isNull();
        assertThat(vnmPos.unrealizedPL()).isNull();
        assertThat(vnmPos.quantity()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("FR-017: Historical price gap produces PARTIAL performance point with forward fill rather than failing")
    void dataGapProducesPartialPerformancePoint() {
        Instant t0 = Instant.parse("2026-08-01T00:00:00Z");
        List<TransactionInput> txs = List.of(
                new TransactionInput(UUID.randomUUID(), 1L, TransactionType.DEPOSIT, null, null, null, null, BigDecimal.ZERO, new BigDecimal("100000000"), "VND", t0, t0, null, null),
                new TransactionInput(UUID.randomUUID(), 2L, TransactionType.BUY, fptId, "FPT", new BigDecimal("1000"), new BigDecimal("50000"), BigDecimal.ZERO, null, "VND", t0, t0, null, null)
        );

        LocalDate d1 = LocalDate.parse("2026-08-01");
        LocalDate d2 = LocalDate.parse("2026-08-02"); // missing bar for FPT on day 2
        LocalDate d3 = LocalDate.parse("2026-08-03");

        List<LocalDate> tradingDates = List.of(d1, d2, d3);
        Map<UUID, Map<LocalDate, BigDecimal>> dailyCloses = Map.of(
                fptId, Map.of(
                        d1, new BigDecimal("50000"),
                        d3, new BigDecimal("55000")
                )
        );

        PerformanceHistoryResult result = PortfolioAnalyticsV1.calculatePerformanceHistory(
                txs, tradingDates, dailyCloses, d1, d3);

        List<DrawdownPoint> series = result.series();
        assertThat(series).hasSize(3);

        assertThat(series.get(0).partial()).isFalse();
        assertThat(series.get(1).partial()).isTrue(); // Day 2 forward fills day 1 close
        assertThat(series.get(1).totalValue()).isEqualByComparingTo(series.get(0).totalValue());
        assertThat(series.get(2).partial()).isFalse();
    }
}
