package com.minhnb.finvera_be.stock.controller;

import com.minhnb.finvera_be.stock.dto.StockChartResponse;
import com.minhnb.finvera_be.stock.dto.StockOverviewResponse;
import com.minhnb.finvera_be.stock.dto.StockSearchResponse;
import com.minhnb.finvera_be.stock.dto.StockTechnicalResponse;
import com.minhnb.finvera_be.stock.service.StockChartService;
import com.minhnb.finvera_be.stock.service.StockOverviewService;
import com.minhnb.finvera_be.stock.service.StockSearchService;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import java.net.URI;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** `/api/v1/stocks` per contracts/stock-detail.openapi.yaml. Owner-only, inherited from Feature 001. */
@RestController
@RequestMapping("/api/v1/stocks")
public class StockController {

    private final StockSearchService searchService;
    private final StockOverviewService overviewService;
    private final StockChartService chartService;
    private final TechnicalIndicatorService technicalService;

    public StockController(
            StockSearchService searchService,
            StockOverviewService overviewService,
            StockChartService chartService,
            TechnicalIndicatorService technicalService) {
        this.searchService = searchService;
        this.overviewService = overviewService;
        this.chartService = chartService;
        this.technicalService = technicalService;
    }

    @GetMapping
    StockSearchResponse search(@RequestParam String query, @RequestParam(defaultValue = "20") int limit) {
        int boundedLimit = Math.min(Math.max(limit, 1), 50);
        return StockSearchResponse.from(searchService.search(query, boundedLimit));
    }

    @GetMapping("/{symbol}")
    ResponseEntity<StockOverviewResponse> overview(
            @PathVariable String symbol,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        var overview = overviewService.findBySymbol(symbol).orElseThrow(() -> notSupported(symbol));
        if (matches(ifNoneMatch, overview.coherenceKey())) {
            return ResponseEntity.status(304).eTag(overview.coherenceKey()).build();
        }
        return ResponseEntity.ok().eTag(overview.coherenceKey()).body(StockOverviewResponse.from(overview));
    }

    @GetMapping("/{symbol}/chart")
    ResponseEntity<StockChartResponse> chart(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1Y") String window,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        var chart = chartService.findBySymbol(symbol, window).orElseThrow(() -> notSupported(symbol));
        if (matches(ifNoneMatch, chart.coherenceKey())) {
            return ResponseEntity.status(304).eTag(chart.coherenceKey()).build();
        }
        return ResponseEntity.ok().eTag(chart.coherenceKey()).body(StockChartResponse.from(symbol, chart));
    }

    @GetMapping("/{symbol}/technical")
    ResponseEntity<StockTechnicalResponse> technical(
            @PathVariable String symbol,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        var technical = technicalService.findBySymbol(symbol).orElseThrow(() -> notSupported(symbol));
        if (matches(ifNoneMatch, technical.coherenceKey())) {
            return ResponseEntity.status(304).eTag(technical.coherenceKey()).build();
        }
        return ResponseEntity.ok().eTag(technical.coherenceKey())
                .body(StockTechnicalResponse.from(symbol, technical));
    }

    @ExceptionHandler(StockNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleNotSupported(StockNotSupportedException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "No accepted reference data for the requested symbol");
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Stock not supported");
        problem.setProperty("reasonCode", "STOCK_NOT_SUPPORTED");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    private static StockNotSupportedException notSupported(String symbol) {
        return new StockNotSupportedException(symbol);
    }

    private static boolean matches(String ifNoneMatch, String coherenceKey) {
        if (ifNoneMatch == null) {
            return false;
        }
        return Arrays.stream(ifNoneMatch.split(","))
                .map(String::trim)
                .anyMatch(candidate -> candidate.equals("*") || candidate.equals(coherenceKey)
                        || candidate.equals("\"" + coherenceKey + "\""));
    }

    static final class StockNotSupportedException extends RuntimeException {
        StockNotSupportedException(String symbol) {
            super("Symbol is unknown or unsupported: " + symbol);
        }
    }
}
