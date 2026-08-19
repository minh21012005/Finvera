package com.minhnb.finvera_be.portfolio.dto;

import java.time.Instant;
import java.util.UUID;

public record WatchlistSummaryResponse(
        UUID id,
        String name,
        Instant createdAt,
        int itemCount) {
}
