package com.minhnb.finvera_be.portfolio.service;

import java.util.UUID;

public final class PortfolioExceptions {

    private PortfolioExceptions() {
    }

    public static class PortfolioNotFoundException extends RuntimeException {
        public PortfolioNotFoundException(UUID portfolioId) {
            super("Portfolio not found: " + portfolioId);
        }
    }

    public static class TransactionNotFoundException extends RuntimeException {
        public TransactionNotFoundException(UUID transactionId) {
            super("Transaction not found: " + transactionId);
        }
    }

    public static class WatchlistNotFoundException extends RuntimeException {
        public WatchlistNotFoundException(UUID watchlistId) {
            super("Watchlist not found: " + watchlistId);
        }
    }

    public static class DuplicatePortfolioNameException extends RuntimeException {
        public DuplicatePortfolioNameException(String name) {
            super("Portfolio with name already exists: " + name);
        }
    }

    public static class DuplicateWatchlistNameException extends RuntimeException {
        public DuplicateWatchlistNameException(String name) {
            super("Watchlist with name already exists: " + name);
        }
    }

    public static class UnsupportedInstrumentException extends RuntimeException {
        public UnsupportedInstrumentException(String symbol) {
            super("Unsupported instrument symbol: " + symbol);
        }
    }

    public static class DuplicateSubmissionException extends RuntimeException {
        private final UUID existingTransactionId;

        public DuplicateSubmissionException(String idempotencyKey, UUID existingTransactionId) {
            super("Duplicate submission with idempotency key: " + idempotencyKey);
            this.existingTransactionId = existingTransactionId;
        }

        public UUID getExistingTransactionId() {
            return existingTransactionId;
        }
    }

    public static class PeriodTooLongException extends RuntimeException {
        private final long requestedDays;
        private final long maxDays;

        public PeriodTooLongException(long requestedDays, long maxDays) {
            super("Requested period of " + requestedDays + " days exceeds maximum allowed span of " + maxDays + " days");
            this.requestedDays = requestedDays;
            this.maxDays = maxDays;
        }

        public long getRequestedDays() {
            return requestedDays;
        }

        public long getMaxDays() {
            return maxDays;
        }
    }
}
