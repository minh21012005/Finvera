package com.minhnb.finvera_be.market.provider.tcbs;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only REST port for TCBS iFlash market data.
 *
 * <p>Implementations MUST NOT call trading, account, cash, portfolio, order,
 * or any non-market endpoint. The allowlisted operations are:
 * <ul>
 *   <li>{@code GET /tartarus/v1/tickerCommons?index={N}} for index 1, 2, 3, 5</li>
 * </ul>
 *
 * <p>No raw provider payload, credential, or market value crosses this boundary.
 * All numeric fields are transported as decimal strings to preserve precision.
 */
public interface TcbsRestClient {

    /**
     * Fetches ticker-commons snapshots for all four allowlisted index numbers
     * (1=VNINDEX, 2=VN30, 3=HNXIndex, 5=UpcomIndex).
     *
     * @param tradingDate the expected trading date; used to validate the response
     * @return sanitized response — never null; throws on auth failure or connectivity error
     */
    TickerCommonsResponse fetchTickerCommons(LocalDate tradingDate);

    /**
     * Fetches per-symbol reference data for breadth universe building.
     * Only called from {@code fetchReferenceData}; NOT called during REST polling.
     */
    List<SecurityRecord> fetchSecurities(LocalDate effectiveDate);

    /**
     * Sanitized, provider-neutral representation of the confirmed
     * {@code GET /tartarus/v1/tickerCommons?index={N}} response envelope.
     *
     * <p>All numeric fields are decimal strings. {@code tradingDate} is YYYY-MM-DD.
     * Field names match the confirmed POC schema (see {@code tcbs-capability-summary.json}).
     */
    record TickerCommonsResponse(String tradingDate, List<TickerCommonsItem> data) {
        public TickerCommonsResponse {
            if (tradingDate == null || tradingDate.isBlank()) {
                throw new IllegalArgumentException("TCBS_TRADING_DATE_MISSING");
            }
            data = data == null ? List.of() : List.copyOf(data);
        }
    }

    /**
     * One row from the {@code data} array of a ticker-commons response.
     * Fields are nullable where the POC observed them as conditionally present.
     *
     * <p>Confirmed fields from POC: symbol, indexNumber, matchPrice, change,
     * changePercent, totalVol, totalVal, open, high, low, refPrice.
     *
     * @param matchPrice nullable — null or absent means price is unavailable (BREADTH_RECORD_INCOMPLETE)
     * @param refPrice   nullable — null or "0" means reference is unavailable (REFERENCE_UNAVAILABLE)
     */
    record TickerCommonsItem(
            String symbol,
            int indexNumber,
            String matchPrice,
            String change,
            String changePercent,
            String totalVol,
            String totalVal,
            String open,
            String high,
            String low,
            String refPrice) { }

    /**
     * Minimal security reference record for breadth universe.
     */
    record SecurityRecord(String symbol, String venue) { }
}
