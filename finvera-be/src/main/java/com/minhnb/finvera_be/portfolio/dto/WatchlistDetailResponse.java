package com.minhnb.finvera_be.portfolio.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WatchlistDetailResponse(
        UUID id,
        String name,
        List<WatchlistItemResponse> items,
        String coherenceKey,
        Instant asOf) {
}
