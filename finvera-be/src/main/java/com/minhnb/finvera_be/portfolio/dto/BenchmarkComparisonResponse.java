package com.minhnb.finvera_be.portfolio.dto;

public record BenchmarkComparisonResponse(
        String portfolioReturn,
        String benchmarkReturn,
        String benchmarkSymbol) {
}
