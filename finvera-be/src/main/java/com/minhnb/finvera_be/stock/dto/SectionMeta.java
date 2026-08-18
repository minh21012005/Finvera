package com.minhnb.finvera_be.stock.dto;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Shared envelope every stock-detail section carries (contracts/stock-detail.openapi.yaml
 * `SectionMeta`): contract version, as-of time, freshness, coherence key, and reason codes.
 */
public record SectionMeta(
        String contractVersion,
        String symbol,
        Instant asOf,
        LocalDate tradingDate,
        String timezone,
        DataStatus dataStatus,
        String coherenceKey,
        List<String> sources,
        List<String> reasonCodes) {

    public static SectionMeta of(
            String symbol,
            Instant asOf,
            LocalDate tradingDate,
            DataStatus dataStatus,
            String coherenceKey,
            List<String> sources,
            List<String> reasonCodes) {
        return new SectionMeta("1.0", symbol, asOf, tradingDate, "Asia/Ho_Chi_Minh", dataStatus, coherenceKey,
                sources, reasonCodes);
    }
}
