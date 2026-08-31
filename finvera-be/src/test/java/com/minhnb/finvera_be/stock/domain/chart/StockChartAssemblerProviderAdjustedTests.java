package com.minhnb.finvera_be.stock.domain.chart;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.stock.domain.chart.StockChartAssembler.BarInput;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.AdjustmentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ADR-0013 (Feature 021): VCI serves one corporate-action-adjusted series per symbol. */
class StockChartAssemblerProviderAdjustedTests {

    private final StockChartAssembler assembler = new StockChartAssembler();

    private static BarInput bar(String date, String close, AdjustmentStatus status) {
        return new BarInput(LocalDate.parse(date), new BigDecimal("100.000000"), new BigDecimal("101.000000"),
                new BigDecimal("99.000000"), new BigDecimal(close), null, null, 1_000L, status);
    }

    @Test
    void aUniformProviderAdjustedSeriesIsServedAsIsUnderItsHonestLabel() {
        var result = assembler.assemble(List.of(
                bar("2026-08-27", "100.500000", AdjustmentStatus.PROVIDER_ADJUSTED),
                bar("2026-08-28", "100.700000", AdjustmentStatus.PROVIDER_ADJUSTED)), "1M");

        assertThat(result.seriesAdjustmentStatus()).isEqualTo(AdjustmentStatus.PROVIDER_ADJUSTED);
        assertThat(result.reasonCode()).isNull();                    // one basis -> nothing to disclose
        assertThat(result.bars()).hasSize(2);
        assertThat(result.bars().get(1).close()).isEqualByComparingTo(new BigDecimal("100.700000"));
    }

    @Test
    void aMixedProviderAdjustedAndRawWindowIsDisclosedNotSpliced() {
        // The KBS->VCI transition can leave a KBS RAW bar on a date VCI does not serve.
        var result = assembler.assemble(List.of(
                bar("2026-08-27", "100.500000", AdjustmentStatus.RAW),
                bar("2026-08-28", "100.700000", AdjustmentStatus.PROVIDER_ADJUSTED)), "1M");

        assertThat(result.seriesAdjustmentStatus()).isEqualTo(AdjustmentStatus.RAW);
        assertThat(result.reasonCode()).isEqualTo("ADJUSTMENT_BASIS_UNAVAILABLE");
    }
}
