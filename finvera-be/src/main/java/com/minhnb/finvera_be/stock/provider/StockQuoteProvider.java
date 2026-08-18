package com.minhnb.finvera_be.stock.provider;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-only current-price port (contracts/stock-data-provider.md), extending
 * the Feature 001 TCBS adapter contract from index subjects to instrument
 * subjects (research R-001). No trading, account, cash, or order operation
 * is reachable through this interface (SEC-003).
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
