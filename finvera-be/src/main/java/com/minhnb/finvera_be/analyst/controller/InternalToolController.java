package com.minhnb.finvera_be.analyst.controller;

import com.minhnb.finvera_be.analyst.dto.ToolResponseDtos.*;
import com.minhnb.finvera_be.analyst.service.ToolDelegateService;
import com.minhnb.finvera_be.portfolio.service.OwnerScopedAccess;
import com.minhnb.finvera_be.research.dto.RetrieveRequest;
import com.minhnb.finvera_be.research.dto.RetrieveResponse;
import com.minhnb.finvera_be.research.service.RetrievalService;
import com.minhnb.finvera_be.stock.dto.ScreenRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller exposing internal tool endpoints for the orchestrator (finvera-ai).
 * Protected by InternalApiKeyFilter (X-Internal-Api-Key).
 *
 * <p>Every endpoint validates the caller-supplied {@code ownerId} against the single
 * configured owner (U-2, orchestration-v1) before delegating. In this private
 * single-owner deployment the two values can never legitimately differ, but the check
 * is enforced explicitly rather than silently ignored, matching the pattern Features
 * 005/006 already establish and keeping this module ready for the day a mismatch would
 * actually be possible.</p>
 */
@RestController
@RequestMapping("/internal/v1/tools")
public class InternalToolController {

    private final ToolDelegateService toolDelegateService;
    private final RetrievalService retrievalService;
    private final OwnerScopedAccess ownerScopedAccess;

    public InternalToolController(
            ToolDelegateService toolDelegateService,
            RetrievalService retrievalService,
            OwnerScopedAccess ownerScopedAccess) {
        this.toolDelegateService = toolDelegateService;
        this.retrievalService = retrievalService;
        this.ownerScopedAccess = ownerScopedAccess;
    }

    private void requireOwner(UUID ownerId) {
        if (ownerId == null || !ownerScopedAccess.isCurrentOwner(ownerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown owner");
        }
    }

    @GetMapping("/market/overview")
    public ResponseEntity<MarketOverviewToolResponse> getMarketOverview(
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        requireOwner(ownerId);
        return ResponseEntity.ok(toolDelegateService.getMarketOverview());
    }

    @GetMapping("/stocks/{symbol}")
    public ResponseEntity<StockSummaryToolResponse> getStockSummary(
            @PathVariable("symbol") String symbol,
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        requireOwner(ownerId);
        return ResponseEntity.ok(toolDelegateService.getStockSummary(symbol));
    }

    @GetMapping("/stocks/{symbol}/technical")
    public ResponseEntity<TechnicalToolResponse> getTechnical(
            @PathVariable("symbol") String symbol,
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        requireOwner(ownerId);
        return ResponseEntity.ok(toolDelegateService.getTechnical(symbol));
    }

    @GetMapping("/stocks/{symbol}/fundamentals")
    public ResponseEntity<FundamentalsToolResponse> getFundamentals(
            @PathVariable("symbol") String symbol,
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        requireOwner(ownerId);
        return ResponseEntity.ok(toolDelegateService.getFundamentals(symbol));
    }

    @GetMapping("/stocks/{symbol}/valuation")
    public ResponseEntity<ValuationToolResponse> getValuation(
            @PathVariable("symbol") String symbol,
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        requireOwner(ownerId);
        return ResponseEntity.ok(toolDelegateService.getValuation(symbol));
    }

    @GetMapping("/portfolios/positions")
    public ResponseEntity<PortfolioPositionsToolResponse> getPortfolioPositions(
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        requireOwner(ownerId);
        return ResponseEntity.ok(toolDelegateService.getPortfolioPositions(ownerId));
    }

    @GetMapping("/portfolios/analytics")
    public ResponseEntity<PortfolioAnalyticsToolResponse> getPortfolioAnalytics(
            @RequestParam(name = "ownerId", required = false) UUID ownerId) {
        requireOwner(ownerId);
        return ResponseEntity.ok(toolDelegateService.getPortfolioAnalytics(ownerId));
    }

    @GetMapping("/research/news")
    public ResponseEntity<NewsBrowseToolResponse> getNewsArticles(
            @RequestParam(name = "ownerId", required = false) UUID ownerId,
            @RequestParam(name = "symbol", required = false) String symbol,
            @RequestParam(name = "limit", required = false, defaultValue = "5") int limit) {
        requireOwner(ownerId);
        return ResponseEntity.ok(toolDelegateService.getNewsArticles(ownerId, symbol, limit));
    }

    /**
     * Backs the RESEARCH_RAG tool by reusing Feature 006's own {@link RetrievalService}
     * unmodified — the same owner-scoped Postgres content-resolution path
     * {@code /api/v1/research/retrieve} uses, so the orchestrator receives real
     * {@code excerpt} text (never available from Qdrant/finvera-ai's own retrieval
     * module alone) rather than reimplementing retrieval or reaching into finvera-ai's
     * internals directly.
     */
    @PostMapping("/research/retrieve")
    public ResponseEntity<RetrieveResponse> retrieveResearchPassages(
            @RequestParam(name = "ownerId", required = false) UUID ownerId,
            @RequestBody RetrieveRequest request) {
        requireOwner(ownerId);
        return ResponseEntity.ok(retrievalService.retrievePassages(request));
    }

    @PostMapping("/screener/executions")
    public ResponseEntity<ScreenerExecutionToolResponse> executeScreener(
            @RequestParam(name = "ownerId", required = false) UUID ownerId,
            @RequestBody(required = false) ScreenRequest request) {
        requireOwner(ownerId);
        return ResponseEntity.ok(toolDelegateService.executeScreener(request));
    }
}
