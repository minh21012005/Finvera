package com.minhnb.finvera_be.research.dto;

import java.util.List;

public record DocumentPageResponse(
        List<DocumentResponse> items,
        long totalCount,
        int limit,
        int offset) {
}
