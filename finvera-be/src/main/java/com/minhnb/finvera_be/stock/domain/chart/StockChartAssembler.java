package com.minhnb.finvera_be.stock.domain.chart;

import com.minhnb.finvera_be.stock.domain.model.StockTypes.AdjustmentStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * FR-003, DATA-008; research R-004. A returned window declares exactly one
 * {@code adjustmentStatus} for its whole series. If the accepted bars in the
 * window do not all share the same basis, the whole series is served RAW
 * with {@code ADJUSTMENT_BASIS_UNAVAILABLE} rather than spliced.
 */
public final class StockChartAssembler {

    private static final int DISPLAY_SCALE = 6;

    public ChartResult assemble(List<BarInput> bars, String requestedWindow) {
        Objects.requireNonNull(bars, "bars");
        Objects.requireNonNull(requestedWindow, "requestedWindow");

        List<BarInput> ordered = bars.stream()
                .sorted(Comparator.comparing(BarInput::tradingDate))
                .toList();
        if (ordered.isEmpty()) {
            return new ChartResult(AdjustmentStatus.UNKNOWN, List.of(), null);
        }

        Set<AdjustmentStatus> statuses = ordered.stream().map(BarInput::adjustmentStatus)
                .collect(java.util.stream.Collectors.toSet());
        boolean uniform = statuses.size() == 1;
        boolean useAdjusted = uniform && statuses.contains(AdjustmentStatus.ADJUSTED)
                && ordered.stream().allMatch(bar -> bar.adjustedClose() != null && bar.adjustmentFactor() != null);

        if (useAdjusted) {
            List<ChartBar> chartBars = ordered.stream().map(StockChartAssembler::toAdjustedBar).toList();
            return new ChartResult(AdjustmentStatus.ADJUSTED, chartBars, null);
        }

        List<ChartBar> rawBars = ordered.stream().map(StockChartAssembler::toRawBar).toList();
        boolean cleanRaw = uniform && statuses.contains(AdjustmentStatus.RAW);
        return new ChartResult(AdjustmentStatus.RAW, rawBars, cleanRaw ? null : "ADJUSTMENT_BASIS_UNAVAILABLE");
    }

    private static ChartBar toAdjustedBar(BarInput bar) {
        BigDecimal factor = bar.adjustmentFactor();
        return new ChartBar(bar.tradingDate(), scale(bar.openPrice().multiply(factor)),
                scale(bar.highPrice().multiply(factor)), scale(bar.lowPrice().multiply(factor)),
                scale(bar.adjustedClose()), bar.volume());
    }

    private static ChartBar toRawBar(BarInput bar) {
        return new ChartBar(bar.tradingDate(), bar.openPrice(), bar.highPrice(), bar.lowPrice(),
                bar.closePrice(), bar.volume());
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    public record BarInput(
            java.time.LocalDate tradingDate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal adjustedClose,
            BigDecimal adjustmentFactor,
            Long volume,
            AdjustmentStatus adjustmentStatus) {
    }

    public record ChartBar(
            java.time.LocalDate tradingDate,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            Long volume) {
    }

    public record ChartResult(AdjustmentStatus seriesAdjustmentStatus, List<ChartBar> bars, String reasonCode) {
        public ChartResult {
            bars = List.copyOf(bars);
        }
    }
}
