package com.minhnb.finvera_be.portfolio.controller;

import com.minhnb.finvera_be.portfolio.dto.PortfolioAnalyticsResponse;
import com.minhnb.finvera_be.portfolio.service.PortfolioAnalyticsService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for portfolio analytics, returns, drawdown, concentration, risk exposure,
 * and VN-Index benchmark comparison.
 * Path: {@code /api/v1/portfolios/{portfolioId}/analytics} per OpenAPI contract.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/analytics")
public class PortfolioAnalyticsController {

    private final PortfolioAnalyticsService analyticsService;

    public PortfolioAnalyticsController(PortfolioAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public PortfolioAnalyticsResponse getPortfolioAnalytics(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.getPortfolioAnalytics(portfolioId, from, to);
    }
}
