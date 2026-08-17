package com.minhnb.finvera_be.market.controller;

import com.minhnb.finvera_be.market.dto.MarketOverviewResponse;
import com.minhnb.finvera_be.market.service.MarketOverviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market")
public class MarketOverviewController {

    private final MarketOverviewService overviewService;

    public MarketOverviewController(MarketOverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping("/overview")
    ResponseEntity<MarketOverviewResponse> overview(
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        MarketOverviewService.MarketOverview overview = overviewService.latest();
        if (matches(ifNoneMatch, overview.etag())) {
            return ResponseEntity.status(304).eTag(overview.etag()).build();
        }
        return ResponseEntity.ok().eTag(overview.etag()).body(MarketOverviewResponse.from(overview));
    }

    private static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null) {
            return false;
        }
        return java.util.Arrays.stream(ifNoneMatch.split(","))
                .map(String::trim)
                .anyMatch(candidate -> candidate.equals("*") || candidate.equals(etag)
                        || candidate.equals("\"" + etag + "\""));
    }
}
