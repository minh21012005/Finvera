package com.minhnb.finvera_be.portfolio.dto;

import java.time.Instant;
import java.util.UUID;

public record PortfolioSummaryResponse(
        UUID id,
        String name,
        Instant createdAt,
        String totalValue,
        String cashBalance,
        String totalUnrealizedPL,
        String totalRealizedPL,
        Instant asOf) {
}
