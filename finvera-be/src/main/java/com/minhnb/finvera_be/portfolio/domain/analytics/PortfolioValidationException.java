package com.minhnb.finvera_be.portfolio.domain.analytics;

import java.util.Objects;

public class PortfolioValidationException extends RuntimeException {

    public enum ReasonCode {
        INSUFFICIENT_POSITION,
        INSUFFICIENT_CASH_BALANCE,
        LOT_ALREADY_CONSUMED,
        ALREADY_VOIDED,
        CANNOT_VOID_VOID,
        TRANSACTION_NOT_FOUND,
        INVALID_TRANSACTION_TYPE,
        DUPLICATE_SUBMISSION
    }

    private final ReasonCode reasonCode;

    public PortfolioValidationException(ReasonCode reasonCode, String message) {
        super(message);
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
    }

    public ReasonCode getReasonCode() {
        return reasonCode;
    }
}
