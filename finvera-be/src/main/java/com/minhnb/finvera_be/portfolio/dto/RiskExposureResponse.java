package com.minhnb.finvera_be.portfolio.dto;

public record RiskExposureResponse(
        Integer riskExposureScore,
        String riskExposureLevel,
        String coverageRatio,
        String reasonCode) {
}
