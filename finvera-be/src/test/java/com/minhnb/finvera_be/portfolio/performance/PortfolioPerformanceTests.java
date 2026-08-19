package com.minhnb.finvera_be.portfolio.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.TransactionInput;
import com.minhnb.finvera_be.portfolio.domain.model.PortfolioTypes.TransactionType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortfolioPerformanceTests {

    @Test
    @DisplayName("NFR-001/NFR-002: Replay and analytics on 500+ transactions across 20+ symbols completes in under 100ms")
    void largeLedgerPerformanceSmoke() {
        List<UUID> instruments = new ArrayList<>();
        Map<UUID, String> symbols = new HashMap<>();
        Map<UUID, BigDecimal> currentPrices = new HashMap<>();

        for (int i = 1; i <= 25; i++) {
            UUID id = UUID.randomUUID();
            instruments.add(id);
            symbols.put(id, "SYM" + i);
            currentPrices.put(id, new BigDecimal("50000"));
        }

        List<TransactionInput> txs = new ArrayList<>();
        Instant baseTime = Instant.parse("2024-01-01T00:00:00Z");

        // Initial deposit
        txs.add(new TransactionInput(
                UUID.randomUUID(), 1L, TransactionType.DEPOSIT,
                null, null, null, null, BigDecimal.ZERO,
                new BigDecimal("10000000000"), "VND",
                baseTime, baseTime, null, null));

        long seq = 2;
        // Generate 500 BUY and SELL transactions
        for (int i = 0; i < 500; i++) {
            UUID inst = instruments.get(i % instruments.size());
            TransactionType type = (i % 3 == 0) ? TransactionType.SELL : TransactionType.BUY;
            BigDecimal qty = new BigDecimal("100");
            BigDecimal price = new BigDecimal("50000");
            BigDecimal fee = new BigDecimal("5000");
            Instant txTime = baseTime.plusSeconds((i + 1) * 3600 * 24);

            txs.add(new TransactionInput(
                    UUID.randomUUID(), seq++, type,
                    inst, symbols.get(inst), qty, price, fee,
                    null, "VND", txTime, txTime, null, null));
        }

        long start = System.nanoTime();
        var holdings = PortfolioAnalyticsV1.replayHoldings(txs, currentPrices, symbols);
        long replayDurationNanos = System.nanoTime() - start;

        assertThat(holdings.totals().totalValue()).isNotNull();
        assertThat(Duration.ofNanos(replayDurationNanos).toMillis()).isLessThan(100);
    }
}
