package com.minhnb.finvera_be.stock.provider.tcbs;

import com.minhnb.finvera_be.market.provider.MarketDataProvider;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsHttpRestClient;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsRestClient.TickerCommonsItem;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsRestClient.TickerCommonsResponse;
import com.minhnb.finvera_be.stock.provider.StockQuoteProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Live per-instrument quote adapter (research.md R-012 gate G-03, closed 2026-08-22): reuses
 * Feature 001's TCBS session/HTTP machinery ({@link TcbsHttpRestClient}) rather than adding a
 * second live provider, per R-001. The owner-run probe confirmed {@code tickerCommons?tickers=}
 * returns the identical field shape already proven for index subjects — same {@code matchPrice}/
 * {@code refPrice}/{@code totalVol}/{@code totalVal} mapping as {@code TcbsMarketDataProvider}.
 *
 * <p>TCBS's REST response carries no session-state field for this endpoint either (T045's own
 * finding: only the opaque WebSocket stream hints at one, and that hint is never trusted) — {@code
 * sessionIndication} is reported {@code "UNKNOWN"} rather than guessed from the trading calendar,
 * consistent with this endpoint's already-approved reconciliation-only session semantics.
 *
 * <p>{@link #fetchCurrentBar} exists alongside {@link #getQuote} because {@code
 * StockQuoteProvider.QuoteObservation} (fixed by the already-shipped fixture contract) has no
 * open/high/low fields, while {@code StockIngestionService#ingestDailyBar} needs a full bar. Both
 * methods read the exact same response item; only the returned shape differs.
 */
public final class TcbsStockQuoteProvider implements StockQuoteProvider {

    public static final String SOURCE = "TCBS_IFLASH_STOCK_DATA";

    private final TcbsHttpRestClient restClient;
    private final Clock clock;

    public TcbsStockQuoteProvider(TcbsHttpRestClient restClient, Clock clock) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public QuoteObservation getQuote(String symbol) {
        TickerCommonsItem item = resolveItem(symbol);
        if (item.matchPrice() == null || item.matchPrice().isBlank()) {
            // Decision A (TcbsMarketDataProvider): exclude, never zero-fill a missing price.
            throw new QuoteUnavailableException(item.symbol(), "MISSING_MATCH_PRICE");
        }

        BigDecimal lastPrice = parseDecimal(item.matchPrice());
        BigDecimal refPrice = parseDecimalOrNull(item.refPrice());
        Long sessionVolume = parseLongOrNull(item.totalVol());
        BigDecimal sessionValueVnd = parseDecimalOrNull(item.totalVal());

        return new QuoteObservation(
                item.symbol().toUpperCase(), lastPrice, refPrice, sessionVolume, sessionValueVnd,
                clock.instant(), "UNKNOWN");
    }

    /**
     * A full current-session bar for ingestion (source {@value #SOURCE}). {@code open}/{@code
     * high}/{@code low} come straight from TCBS's own response fields — never derived from {@code
     * matchPrice} — so a still-forming intraday bar is never misrepresented as a completed one.
     * Missing open/high/low falls back to the match price only when TCBS omits that specific
     * field (observed as possible for some symbols in the G-03 evidence), never fabricated
     * out of thin air.
     */
    public LiveDailyBar fetchCurrentBar(String symbol, LocalDate tradingDate) {
        Objects.requireNonNull(tradingDate, "tradingDate");
        TickerCommonsItem item = resolveItem(symbol);
        if (item.matchPrice() == null || item.matchPrice().isBlank()) {
            throw new QuoteUnavailableException(item.symbol(), "MISSING_MATCH_PRICE");
        }
        BigDecimal close = parseDecimal(item.matchPrice());
        BigDecimal open = parseDecimalOrNull(item.open());
        BigDecimal high = parseDecimalOrNull(item.high());
        BigDecimal low = parseDecimalOrNull(item.low());

        return new LiveDailyBar(
                item.symbol().toUpperCase(), tradingDate, clock.instant(),
                open != null ? open : close, high != null ? high : close, low != null ? low : close, close,
                parseLongOrNull(item.totalVol()), parseDecimalOrNull(item.totalVal()));
    }

    private TickerCommonsItem resolveItem(String symbol) {
        Objects.requireNonNull(symbol, "symbol");
        String normalizedSymbol = symbol.toUpperCase();
        TickerCommonsResponse response = fetchTickerCommons(normalizedSymbol);
        return response.data().stream()
                .filter(candidate -> normalizedSymbol.equalsIgnoreCase(candidate.symbol()))
                .findFirst()
                .orElseThrow(() -> new SymbolNotReturnedException(normalizedSymbol));
    }

    private TickerCommonsResponse fetchTickerCommons(String normalizedSymbol) {
        try {
            return restClient.fetchTickerCommonsForSymbols(List.of(normalizedSymbol));
        } catch (MarketDataProvider.ProviderAuthenticationRequiredException e) {
            // Translated to the stock module's own exception type so callers depending only on
            // StockQuoteProvider never need to know this adapter reuses Feature 001's session.
            throw new ProviderAuthenticationRequiredException();
        }
    }

    private static BigDecimal parseDecimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new QuoteUnavailableException(null, "TCBS_DECIMAL_INVALID");
        }
    }

    private static BigDecimal parseDecimalOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long result = new BigDecimal(value).longValueExact();
            return result < 0 ? null : result;
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    /** The symbol was requested but absent from TCBS's response data array (e.g. delisted/unknown to TCBS). */
    public static final class SymbolNotReturnedException extends RuntimeException {
        public SymbolNotReturnedException(String symbol) {
            super("TCBS did not return quote data for symbol: " + symbol);
        }
    }

    /** The symbol was returned but its price fields could not be used (e.g. missing match price). */
    public static final class QuoteUnavailableException extends RuntimeException {
        public QuoteUnavailableException(String symbol, String reasonCode) {
            super("TCBS quote unavailable for " + symbol + ": " + reasonCode);
        }
    }

    public record LiveDailyBar(
            String symbol, LocalDate tradingDate, java.time.Instant observedAt,
            BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
            Long volume, BigDecimal valueVnd) {
    }
}
