package com.minhnb.finvera_be.analyst.controller;

import com.minhnb.finvera_be.analyst.dto.ToolResponseDtos.*;
import com.minhnb.finvera_be.analyst.service.ToolDelegateService;
import com.minhnb.finvera_be.stock.dto.ScreenRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing internal tool endpoints for the orchestrator (finvera-ai).
 * Protected by InternalApiKeyFilter (X-Internal-Api-Key).
 */
@RestController
@RequestMapping("/internal/v1/tools")
public class InternalToolController {

    private final ToolDelegateService toolDelegateService;

    public InternalToolController(ToolDelegateService toolDelegateService) {
        this.toolDelegateService = toolDelegateService;
    }

    @GetMapping("/market/overview")
    public ResponseEntity<MarketOverviewToolResponse> getMarketOverview(
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        return ResponseEntity.ok(toolDelegateService.getMarketOverview());
    }

    @GetMapping("/stocks/{symbol}")
    public ResponseEntity<StockSummaryToolResponse> getStockSummary(
            @PathVariable("symbol") String symbol,
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        return ResponseEntity.ok(toolDelegateService.getStockSummary(symbol));
    }

    @GetMapping("/stocks/{symbol}/technical")
    public ResponseEntity<TechnicalToolResponse> getTechnical(
            @PathVariable("symbol") String symbol,
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        return ResponseEntity.ok(toolDelegateService.getTechnical(symbol));
    }

    @GetMapping("/stocks/{symbol}/fundamentals")
    public ResponseEntity<FundamentalsToolResponse> getFundamentals(
            @PathVariable("symbol") String symbol,
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        return ResponseEntity.ok(toolDelegateService.getFundamentals(symbol));
    }

    @GetMapping("/stocks/{symbol}/valuation")
    public ResponseEntity<ValuationToolResponse> getValuation(
            @PathVariable("symbol") String symbol,
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        return ResponseEntity.ok(toolDelegateService.getValuation(symbol));
    }

    @GetMapping("/portfolios/positions")
    public ResponseEntity<PortfolioPositionsToolResponse> getPortfolioPositions(
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        return ResponseEntity.ok(toolDelegateService.getPortfolioPositions(ownerId));
    }

    @GetMapping("/portfolios/analytics")
    public ResponseEntity<PortfolioAnalyticsToolResponse> getPortfolioAnalytics(
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        return ResponseEntity.ok(toolDelegateService.getPortfolioAnalytics(ownerId));
    }

    @GetMapping("/research/news")
    public ResponseEntity<NewsBrowseToolResponse> getNewsArticles(
            @RequestParam(name = "ownerId", required = false) UUID ownerId,
            @RequestParam(name = "symbol", required = false) String symbol,
            @RequestParam(name = "limit", required = false, defaultValue = "5") int limit) {
        return ResponseEntity.ok(toolDelegateService.getNewsArticles(ownerId, symbol, limit));
    }

    @PostMapping("/screener/executions")
    public ResponseEntity<ScreenerExecutionToolResponse> executeScreener(
            @RequestBody(required = false) ScreenRequest request) {
        return ResponseEntity.ok(toolDelegateService.executeScreener(request));
    }
}
