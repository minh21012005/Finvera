package com.minhnb.finvera_be.stock.provider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only corporate-action port (contracts/stock-data-provider.md). The
 * adapter computes no adjustment factor; the domain derives it so the factor
 * is reproducible from accepted facts (research R-004).
 */
public interface CorporateActionProvider {

    List<CorporateAction> getCorporateActions(String symbol, LocalDate fromDate, LocalDate toDate);

    record CorporateAction(
            String actionType,
            LocalDate exDate,
            LocalDate recordDate,
            LocalDate paymentDate,
            BigDecimal ratioNumerator,
            BigDecimal ratioDenominator,
            BigDecimal cashAmountVnd,
            String sourceRevision) {
    }
}
