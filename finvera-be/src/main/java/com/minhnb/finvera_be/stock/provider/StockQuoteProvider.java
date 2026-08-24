package com.minhnb.finvera_be.stock.provider;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-only current-price port (contracts/stock-data-provider.md). The active
 * private runtime does not wire a live implementation; Vnstock/KBS facts enter
 * through explicit import boundaries. No trading, account, cash, or order
 * operation is reachable through this interface (SEC-003).
 */
public interface StockQuoteProvider {

    QuoteObservation getQuote(String symbol);

    record QuoteObservation(
            String symbol,
            BigDecimal lastPrice,
            BigDecimal officialReferencePrice,
            Long sessionVolume,
            BigDecimal sessionValueVnd,
            Instant observedAt,
            String sessionIndication) {
    }

    /** Mirrors MarketDataProvider.ProviderAuthenticationRequiredException for the stock module (NFR-007). */
    final class ProviderAuthenticationRequiredException extends RuntimeException {
        public ProviderAuthenticationRequiredException() {
            super("Provider authentication is required");
        }
    }
}
