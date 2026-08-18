package com.minhnb.finvera_be.stock.dto;

import com.minhnb.finvera_be.stock.service.StockSearchService;
import java.util.List;

/** Version 1.0 transport DTO for `GET /stocks` (contracts/stock-detail.openapi.yaml). */
public record StockSearchResponse(String contractVersion, List<ResultResponse> results) {

    public static StockSearchResponse from(List<StockSearchService.Result> results) {
        return new StockSearchResponse("1.0", results.stream().map(ResultResponse::from).toList());
    }

    public record ResultResponse(
            String symbol, String companyName, String exchange, String sector, String listingStatus) {
        static ResultResponse from(StockSearchService.Result result) {
            return new ResultResponse(result.symbol(), result.companyName(), result.exchange(), result.sector(),
                    result.listingStatus());
        }
    }
}
