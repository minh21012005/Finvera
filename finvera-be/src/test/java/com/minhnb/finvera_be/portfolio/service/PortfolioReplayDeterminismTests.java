package com.minhnb.finvera_be.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.PortfolioHoldingsState;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.ReturnResult;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.TransactionInput;
import com.minhnb.finvera_be.portfolio.domain.model.PortfolioTypes.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortfolioReplayDeterminismTests {

    private final UUID fptId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID vnmId = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    @DisplayName("FR-016: Replaying the exact same transaction sequence and market inputs yields byte-for-byte identical state and return metrics")
    void determinismEvaluation() {
        Instant t0 = Instant.parse("2026-08-01T00:00:00Z");
        Instant t1 = Instant.parse("2026-08-02T00:00:00Z");
        Instant t2 = Instant.parse("2026-08-03T00:00:00Z");

        List<TransactionInput> txs = List.of(
                new TransactionInput(UUID.randomUUID(), 1L, TransactionType.DEPOSIT, null, null, null, null, BigDecimal.ZERO, new BigDecimal("100000000"), "VND", t0, t0, null, null),
                new TransactionInput(UUID.randomUUID(), 2L, TransactionType.BUY, fptId, "FPT", new BigDecimal("1000"), new BigDecimal("50000"), new BigDecimal("5000"), null, "VND", t1, t1, null, null),
                new TransactionInput(UUID.randomUUID(), 3L, TransactionType.BUY, vnmId, "VNM", new BigDecimal("500"), new BigDecimal("70000"), new BigDecimal("5000"), null, "VND", t1, t1, null, null),
                new TransactionInput(UUID.randomUUID(), 4L, TransactionType.SELL, fptId, "FPT", new BigDecimal("400"), new BigDecimal("60000"), new BigDecimal("4000"), null, "VND", t2, t2, null, null)
        );

        Map<UUID, BigDecimal> prices = Map.of(
                fptId, new BigDecimal("62000"),
                vnmId, new BigDecimal("72000")
        );
        Map<UUID, String> symbols = Map.of(fptId, "FPT", vnmId, "VNM");

        // Run 1
        PortfolioHoldingsState state1 = PortfolioAnalyticsV1.replayHoldings(txs, prices, symbols);
        ReturnResult ret1 = PortfolioAnalyticsV1.calculateReturns(
                txs, state1.totals().totalValue(), LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03"),
                new BigDecimal("100000000"), state1.totals().totalValue());

        // Run 2
        PortfolioHoldingsState state2 = PortfolioAnalyticsV1.replayHoldings(txs, prices, symbols);
        ReturnResult ret2 = PortfolioAnalyticsV1.calculateReturns(
                txs, state2.totals().totalValue(), LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03"),
                new BigDecimal("100000000"), state2.totals().totalValue());

        // Assert identical values
        assertThat(state1.totals().totalValue()).isEqualByComparingTo(state2.totals().totalValue());
        assertThat(state1.totals().totalRealizedPL()).isEqualByComparingTo(state2.totals().totalRealizedPL());
        assertThat(state1.totals().totalUnrealizedPL()).isEqualByComparingTo(state2.totals().totalUnrealizedPL());
        assertThat(state1.totals().cashBalance()).isEqualByComparingTo(state2.totals().cashBalance());

        assertThat(ret1.returnSinceInception()).isEqualByComparingTo(ret2.returnSinceInception());
        assertThat(ret1.returnOverPeriod()).isEqualByComparingTo(ret2.returnOverPeriod());
    }
}
