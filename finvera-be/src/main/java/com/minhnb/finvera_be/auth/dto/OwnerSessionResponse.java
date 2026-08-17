package com.minhnb.finvera_be.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record OwnerSessionResponse(
        UUID subject,
        String username,
        Instant authenticatedAt,
        Instant expiresAt) {
}
