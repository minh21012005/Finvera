package com.minhnb.finvera_be.analyst.service;

public final class AnalystConversationExceptions {
    private AnalystConversationExceptions() { }

    public static final class ConversationNotFoundException extends RuntimeException {
        public ConversationNotFoundException() { super("Conversation not found"); }
    }

    public static final class ConversationConflictException extends RuntimeException {
        private final String reasonCode;
        private final boolean retryable;
        public ConversationConflictException(String reasonCode, boolean retryable) {
            super("Conversation request conflicts with its current state");
            this.reasonCode = reasonCode; this.retryable = retryable;
        }
        public String reasonCode() { return reasonCode; }
        public boolean retryable() { return retryable; }
    }
}
