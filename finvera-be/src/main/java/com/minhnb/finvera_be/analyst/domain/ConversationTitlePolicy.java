package com.minhnb.finvera_be.analyst.domain;

public final class ConversationTitlePolicy {

    public static final int AUTO_TITLE_MAX_LENGTH = 80;
    public static final int OWNER_TITLE_MAX_LENGTH = 120;

    private ConversationTitlePolicy() {
    }

    public static String automaticTitle(String question) {
        String normalized = normalize(question);
        if (normalized.length() <= AUTO_TITLE_MAX_LENGTH) {
            return normalized;
        }
        String prefix = normalized.substring(0, AUTO_TITLE_MAX_LENGTH + 1);
        int boundary = prefix.lastIndexOf(' ');
        return (boundary > 0 ? prefix.substring(0, boundary) : normalized.substring(0, AUTO_TITLE_MAX_LENGTH)).strip();
    }

    public static String ownerTitle(String title) {
        String normalized = normalize(title);
        if (normalized.length() > OWNER_TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("Title must not exceed 120 characters");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        return normalized;
    }
}
