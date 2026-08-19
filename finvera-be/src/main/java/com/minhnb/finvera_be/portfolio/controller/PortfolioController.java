package com.minhnb.finvera_be.portfolio.controller;

import com.minhnb.finvera_be.portfolio.dto.CreatePortfolioRequest;
import com.minhnb.finvera_be.portfolio.dto.PortfolioSummaryResponse;
import com.minhnb.finvera_be.portfolio.dto.PositionsResponse;
import com.minhnb.finvera_be.portfolio.dto.RenamePortfolioRequest;
import com.minhnb.finvera_be.portfolio.service.PortfolioService;
import com.minhnb.finvera_be.portfolio.service.PositionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for portfolio lifecycle and derived positions.
 * Paths: {@code /api/v1/portfolios} and {@code /api/v1/portfolios/{portfolioId}/positions}
 * per contracts/portfolio-watchlist.openapi.yaml.
 */
@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PositionService positionService;

    public PortfolioController(
            PortfolioService portfolioService,
            PositionService positionService) {
        this.portfolioService = portfolioService;
        this.positionService = positionService;
    }

    @GetMapping
    public List<PortfolioSummaryResponse> listPortfolios() {
        return portfolioService.listPortfolios();
    }

    @PostMapping
    public ResponseEntity<PortfolioSummaryResponse> createPortfolio(
            @Valid @RequestBody CreatePortfolioRequest request) {
        PortfolioSummaryResponse response = portfolioService.createPortfolio(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{portfolioId}")
    public PortfolioSummaryResponse getPortfolio(@PathVariable UUID portfolioId) {
        return portfolioService.getPortfolio(portfolioId);
    }

    @PatchMapping("/{portfolioId}")
    public PortfolioSummaryResponse renamePortfolio(
            @PathVariable UUID portfolioId,
            @Valid @RequestBody RenamePortfolioRequest request) {
        return portfolioService.renamePortfolio(portfolioId, request);
    }

    @DeleteMapping("/{portfolioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePortfolio(@PathVariable UUID portfolioId) {
        portfolioService.deletePortfolio(portfolioId);
    }

    @GetMapping("/{portfolioId}/positions")
    public PositionsResponse getPositions(@PathVariable UUID portfolioId) {
        return positionService.getPositions(portfolioId);
    }
}
