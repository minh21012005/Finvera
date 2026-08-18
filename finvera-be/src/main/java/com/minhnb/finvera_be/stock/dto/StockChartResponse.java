package com.minhnb.finvera_be.stock.dto;

import com.minhnb.finvera_be.stock.domain.chart.StockChartAssembler.ChartBar;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.AdjustmentStatus;
import com.minhnb.finvera_be.stock.service.StockChartService.StockChart;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Version 1.0 transport DTO for `GET /stocks/{symbol}/chart` (contracts/stock-detail.openapi.yaml). */
public record StockChartResponse(
        SectionMeta meta,
        String window,
        AdjustmentStatus adjustmentStatus,
        List<BarResponse> bars) {

    public static StockChartResponse from(String symbol, StockChart chart) {
        List<String> reasonCodes = chart.reasonCode() == null ? List.of() : List.of(chart.reasonCode());
        LocalDate tradingDate = chart.bars().isEmpty() ? null : chart.bars().getLast().tradingDate();
        return new StockChartResponse(
                SectionMeta.of(symbol, chart.asOf(), tradingDate, chart.dataStatus(), chart.coherenceKey(),
                        List.of("FINVERA_ACCEPTED"), reasonCodes),
                chart.window(), chart.adjustmentStatus(),
                chart.bars().stream().map(BarResponse::from).toList());
    }

    public record BarResponse(
            LocalDate tradingDate, String open, String high, String low, String close, Long volume) {
        static BarResponse from(ChartBar bar) {
            return new BarResponse(bar.tradingDate(), decimal(bar.open()), decimal(bar.high()),
                    decimal(bar.low()), decimal(bar.close()), bar.volume());
        }
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
