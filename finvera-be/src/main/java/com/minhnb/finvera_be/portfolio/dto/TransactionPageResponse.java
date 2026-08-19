package com.minhnb.finvera_be.portfolio.dto;

import java.util.List;

public record TransactionPageResponse(
        List<TransactionResponse> items,
        int totalCount,
        int limit,
        int offset) {
}
