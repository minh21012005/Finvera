package com.minhnb.finvera_be.stock.dto;

import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.CategoryDisclosure;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.ScreenExecutionResult;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.ScreenMatch;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Version 1.0 transport DTO for `POST /screener/executions`
 * (contracts/stock-screener.openapi.yaml).
 */
public record ScreenResponse(
        String ruleVersion,
        List<MatchResponse> matches,
        int totalMatchCount,
        int limit,
        int offset,
        List<CategoryDisclosureResponse> categoryDisclosures,
        String coherenceKey,
        String calculatedAt) {

    public static ScreenResponse from(ScreenExecutionResult result, int limit, int offset) {
        List<MatchResponse> matches = result.matches().stream().map(MatchResponse::from).toList();
        List<CategoryDisclosureResponse> disclosures = result.categoryDisclosures().stream()
                .map(CategoryDisclosureResponse::from).toList();
        return new ScreenResponse(ScreenerV1.RULE_VERSION, matches, result.totalMatchCount(), limit, offset,
                disclosures, result.coherenceKey(), result.calculatedAt().toString());
    }

    public record MatchResponse(
            String symbol,
            String companyName,
            String exchange,
            String sectorName,
            Map<String, String> matchedValues,
            String dataStatus,
            LocalDate asOfTradingDate) {
        static MatchResponse from(ScreenMatch match) {
            return new MatchResponse(match.symbol(), match.companyName(), match.exchange(), match.sectorName(),
                    match.matchedValues(), match.dataStatus() == null ? null : match.dataStatus().name(),
                    match.asOfTradingDate());
        }
    }

    public record CategoryDisclosureResponse(String category, String status, String reasonCode, int excludedCount) {
        static CategoryDisclosureResponse from(CategoryDisclosure disclosure) {
            return new CategoryDisclosureResponse(disclosure.category(), disclosure.status().name(),
                    disclosure.reasonCode(), disclosure.excludedCount());
        }
    }
}
