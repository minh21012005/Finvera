package com.minhnb.finvera_be.portfolio.dto;

import java.time.Instant;
import java.util.List;

public record PositionsResponse(
        List<PositionResponse> positions,
        String cashBalance,
        String totalValue,
        String coherenceKey,
        Instant asOf) {
}
