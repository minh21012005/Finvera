package com.minhnb.finvera_be.stock.provider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only fundamental-report port (contracts/stock-data-provider.md). This
 * port has no accepted live implementation; gate G-01 in research.md R-012
 * governs it. The adapter returns source line items before mapping to
 * Finvera metric codes; the mapping happens against the versioned
 * {@code fundamental_metric_catalog} and an unmapped line item is dropped
 * with a counted metric, never guessed into the nearest-looking code.
 */
public interface FundamentalReportProvider {

    List<FundamentalReport> getReports(String symbol, String periodType, LocalDate fromPeriod, LocalDate toPeriod);

    record FundamentalReport(
            String periodType,
            int fiscalYear,
            Integer fiscalQuarter,
            LocalDate periodStart,
            LocalDate periodEnd,
            String reportKind,
            String auditStatus,
            String currency,
            int unitScale,
            List<LineItem> lineItems) {
    }

    record LineItem(String sourceLineItem, BigDecimal value) {
    }

    /** Thrown by any adapter attempting live delivery while G-01 remains open. */
    final class GateNotClosedException extends RuntimeException {
        public GateNotClosedException() {
            super("G-01 fundamental report source gate is not closed; no live adapter is authorized");
        }
    }
}
