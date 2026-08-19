package com.minhnb.finvera_be.portfolio.domain.model;

public final class PortfolioTypes {

    private PortfolioTypes() {
    }

    public enum TransactionType {
        BUY,
        SELL,
        DEPOSIT,
        WITHDRAW,
        VOID
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        UNAVAILABLE
    }

    public enum TotalsAvailability {
        DEFINED,
        NOT_APPLICABLE,
        MISSING
    }
}
