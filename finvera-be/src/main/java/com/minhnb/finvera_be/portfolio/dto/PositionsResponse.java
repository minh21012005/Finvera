package com.minhnb.finvera_be.portfolio.dto;

import java.time.Instant;
import java.util.List;

public record PositionsResponse(
        List<PositionResponse> positions,
        String cashBalance,
        String totalValue,
        String dataStatus,
        List<String> reasonCodes,
        String coherenceKey,
        Instant asOf) {
}
