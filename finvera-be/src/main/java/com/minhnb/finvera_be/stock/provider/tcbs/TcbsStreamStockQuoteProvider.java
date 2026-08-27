package com.minhnb.finvera_be.stock.provider.tcbs;

import com.minhnb.finvera_be.market.service.LiveStockQuoteService;
import com.minhnb.finvera_be.stock.provider.StockQuoteProvider;
import java.util.Objects;

/** Stock-module adapter over the market module's accepted live-quote application API. */
public final class TcbsStreamStockQuoteProvider implements StockQuoteProvider {
    private final LiveStockQuoteService quotes;
    public TcbsStreamStockQuoteProvider(LiveStockQuoteService quotes) { this.quotes = Objects.requireNonNull(quotes); }
    @Override public QuoteObservation getQuote(String symbol) {
        quotes.ensureSubscribed(symbol);
        var quote = quotes.findLatest(symbol).orElseThrow(() -> new QuoteUnavailableException(symbol));
        return new QuoteObservation(quote.symbol(), quote.lastPrice(), quote.referencePrice(),
                quote.openPrice(), quote.highPrice(), quote.lowPrice(),
                quote.sessionVolume(), quote.sessionValueVnd(), quote.observedAt(), "TCBS_THESIS_STREAM");
    }
    public static final class QuoteUnavailableException extends RuntimeException {
        public QuoteUnavailableException(String symbol) { super("Live quote unavailable for " + symbol); }
    }
}
