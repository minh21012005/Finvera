package com.minhnb.finvera_be.portfolio.dto;

public record PositionResponse(
        String instrumentSymbol,
        String quantity,
        String averageCostBasis,
        String currentPrice,
        String currentPriceStatus,
        String unrealizedPL,
        String realizedPL,
        String allocation) {
}
