package com.minhnb.finvera_be.portfolio.dto;

import java.time.LocalDate;

public record PositionResponse(
        String instrumentSymbol,
        String quantity,
        String averageCostBasis,
        String currentPrice,
        String currentPriceStatus,
        String priceDataStatus,
        LocalDate priceTradingDate,
        String unrealizedPL,
        String realizedPL,
        String allocation) {
}
