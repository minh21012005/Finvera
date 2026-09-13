package com.minhnb.finvera_be.positioning.service;

public final class PositionSizingExceptions {
    private PositionSizingExceptions() { }

    public static final class InvalidSizingRequestException extends RuntimeException {
        private final String reasonCode;
        public InvalidSizingRequestException(String reasonCode) { super("Position sizing request is invalid"); this.reasonCode = reasonCode; }
        public String reasonCode() { return reasonCode; }
    }
}
