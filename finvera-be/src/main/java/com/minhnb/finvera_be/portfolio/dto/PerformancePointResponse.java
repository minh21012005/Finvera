package com.minhnb.finvera_be.portfolio.dto;

import java.time.LocalDate;

public record PerformancePointResponse(
        LocalDate date,
        String totalValue,
        String dataStatus,
        String reasonCode) {
}
