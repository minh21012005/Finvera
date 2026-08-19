package com.minhnb.finvera_be.stock.dto;

/**
 * Version 1.0 transport DTO for `POST /strategies/{strategyCode}/scan`
 * (contracts/strategy-signal.openapi.yaml). Every property is optional.
 */
public record ScanRequest(Integer limit, Integer offset) {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    public int effectiveLimit() {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    public int effectiveOffset() {
        return offset == null ? 0 : Math.max(offset, 0);
    }
}
