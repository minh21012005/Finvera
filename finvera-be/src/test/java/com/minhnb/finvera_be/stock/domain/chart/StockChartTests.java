package com.minhnb.finvera_be.stock.domain.chart;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.stock.domain.chart.StockChartAssembler.BarInput;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.AdjustmentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** FR-003, DATA-008; research R-004 (no splicing). */
class StockChartTests {

    private final StockChartAssembler assembler = new StockChartAssembler();

    @Test
    void ordersBarsAscendingByTradingDateRegardlessOfInputOrder() {
        var out = assembler.assemble(List.of(
                rawBar(LocalDate.of(2026, 8, 14)),
                rawBar(LocalDate.of(2026, 8, 12)),
                rawBar(LocalDate.of(2026, 8, 13))), "1M");
        assertThat(out.bars()).extracting(StockChartAssembler.ChartBar::tradingDate)
                .containsExactly(LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 14));
    }

    @Test
    void aUniformRawSeriesPassesThroughUnchanged() {
        var out = assembler.assemble(List.of(rawBar(LocalDate.of(2026, 8, 14))), "1M");
        assertThat(out.seriesAdjustmentStatus()).isEqualTo(AdjustmentStatus.RAW);
        assertThat(out.bars().getFirst().close()).isEqualByComparingTo("100.000000");
        assertThat(out.reasonCode()).isNull();
    }

    @Test
    void aUniformAdjustedSeriesScalesOpenHighLowByTheStoredFactorAndUsesTheStoredAdjustedClose() {
        var bar = new BarInput(LocalDate.of(2026, 7, 1), new BigDecimal("200.000000"),
                new BigDecimal("210.000000"), new BigDecimal("195.000000"), new BigDecimal("204.000000"),
                new BigDecimal("102.000000"), new BigDecimal("0.5"), 1_000_000L, AdjustmentStatus.ADJUSTED);

        var out = assembler.assemble(List.of(bar), "1Y");

        assertThat(out.seriesAdjustmentStatus()).isEqualTo(AdjustmentStatus.ADJUSTED);
        var chartBar = out.bars().getFirst();
        assertThat(chartBar.open()).isEqualByComparingTo("100.000000");
        assertThat(chartBar.high()).isEqualByComparingTo("105.000000");
        assertThat(chartBar.low()).isEqualByComparingTo("97.500000");
        // The stored adjusted_close is authoritative once persisted; it is used directly, not re-derived.
        assertThat(chartBar.close()).isEqualByComparingTo("102.000000");
    }

    @Test
    void aSplitInsideTheWindowNeverProducesASplicedSeries() {
        var beforeSplit = new BarInput(LocalDate.of(2026, 6, 1), new BigDecimal("240000.000000"),
                new BigDecimal("242000.000000"), new BigDecimal("238000.000000"), new BigDecimal("241000.000000"),
                new BigDecimal("120500.000000"), new BigDecimal("0.5"), 500_000L, AdjustmentStatus.ADJUSTED);
        var afterSplit = new BarInput(LocalDate.of(2026, 7, 6), new BigDecimal("120000.000000"),
                new BigDecimal("121000.000000"), new BigDecimal("119000.000000"), new BigDecimal("120500.000000"),
                null, null, 900_000L, AdjustmentStatus.RAW);

        var out = assembler.assemble(List.of(beforeSplit, afterSplit), "3M");

        assertThat(out.seriesAdjustmentStatus()).isEqualTo(AdjustmentStatus.RAW);
        assertThat(out.reasonCode()).isEqualTo("ADJUSTMENT_BASIS_UNAVAILABLE");
        // Every bar reports the raw stored OHLC — the pre-split bar is never silently rescaled.
        assertThat(out.bars().getFirst().close()).isEqualByComparingTo("241000.000000");
        assertThat(out.bars().get(1).close()).isEqualByComparingTo("120500.000000");
    }

    @Test
    void anEmptyWindowIsAValidAnswerNotAnError() {
        var out = assembler.assemble(List.of(), "1M");
        assertThat(out.bars()).isEmpty();
    }

    private static BarInput rawBar(LocalDate tradingDate) {
        return new BarInput(tradingDate, new BigDecimal("98.000000"), new BigDecimal("101.000000"),
                new BigDecimal("97.000000"), new BigDecimal("100.000000"), null, null, 1_000_000L,
                AdjustmentStatus.RAW);
    }
}
