package com.minhnb.finvera_be.research.service;

import java.util.UUID;

public final class ResearchExceptions {

    private ResearchExceptions() {
    }

    public static class DocumentNotFoundException extends RuntimeException {
        public DocumentNotFoundException(UUID documentId) {
            super("Document not found: " + documentId);
        }
    }

    public static class NewsArticleNotFoundException extends RuntimeException {
        public NewsArticleNotFoundException(UUID articleId) {
            super("News article not found: " + articleId);
        }
    }

    public static class DuplicateSubmissionException extends RuntimeException {
        private final UUID existingItemId;

        public DuplicateSubmissionException(UUID existingItemId) {
            super("Duplicate submission for idempotency key. Original item id: " + existingItemId);
            this.existingItemId = existingItemId;
        }

        public UUID getExistingItemId() {
            return existingItemId;
        }
    }

    public static class RetrievalUnavailableException extends RuntimeException {
        public RetrievalUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
