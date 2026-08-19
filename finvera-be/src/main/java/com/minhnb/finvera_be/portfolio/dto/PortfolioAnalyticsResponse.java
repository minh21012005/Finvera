package com.minhnb.finvera_be.portfolio.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PortfolioAnalyticsResponse(
        LocalDate periodFrom,
        LocalDate periodTo,
        boolean periodClampedToInception,
        String returnSinceInception,
        String returnOverPeriod,
        String returnMethodDisclosureCode,
        String maxDrawdown,
        List<PerformancePointResponse> performanceHistory,
        List<ConcentrationEntryResponse> stockConcentration,
        List<ConcentrationEntryResponse> sectorConcentration,
        RiskExposureResponse riskExposure,
        BenchmarkComparisonResponse benchmark,
        Instant asOf) {
}
