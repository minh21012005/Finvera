package com.minhnb.finvera_be.portfolio.dto;

import java.time.Instant;

public record WatchlistItemResponse(
        String symbol,
        String companyName,
        Instant addedAt,
        String currentPrice,
        String dailyChangePercent,
        String technicalTrend,
        String volumeCondition,
        boolean hasCurrentSignal,
        String signalDirection,
        String riskLevel,
        String dataStatus,
        String reasonCode) {
}
