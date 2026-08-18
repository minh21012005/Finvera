package com.minhnb.finvera_be.stock.provider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read-only completed-session daily bar port (contracts/stock-data-provider.md). */
public interface StockHistoryProvider {

    List<DailyBar> getDailyBars(String symbol, LocalDate fromDate, LocalDate toDate);

    record DailyBar(
            LocalDate tradingDate,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            Long volume,
            BigDecimal valueVnd,
            String adjustmentIndication) {
    }
}
