package com.minhnb.finvera_be.stock.dto;

import com.minhnb.finvera_be.stock.dto.StockSignalsResponse.SignalResponse;
import com.minhnb.finvera_be.stock.service.strategy.StrategyScanService.ScanMatch;
import com.minhnb.finvera_be.stock.service.strategy.StrategyScanService.ScanResult;
import java.util.List;

/**
 * Version 1.0 transport DTO for `POST /strategies/{strategyCode}/scan`
 * (contracts/strategy-signal.openapi.yaml).
 */
public record ScanResponse(
        String strategyCode,
        List<ScanMatchResponse> matches,
        int totalMatchCount,
        int limit,
        int offset,
        int excludedForInsufficientHistoryCount,
        String calculatedAt) {

    public static ScanResponse from(ScanResult result) {
        List<ScanMatchResponse> matches = result.matches().stream().map(ScanMatchResponse::from).toList();
        return new ScanResponse(result.strategyCode().name(), matches, result.totalMatchCount(), result.limit(),
                result.offset(), result.excludedForInsufficientHistoryCount(), result.calculatedAt().toString());
    }

    public record ScanMatchResponse(String symbol, String companyName, String exchange, SignalResponse signal) {
        static ScanMatchResponse from(ScanMatch match) {
            return new ScanMatchResponse(match.symbol(), match.companyName(), match.exchange(),
                    SignalResponse.from(match.signal()));
        }
    }
}
