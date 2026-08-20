package com.minhnb.finvera_be.research.dto;

import java.time.Instant;
import java.util.List;

public record RetrieveResponse(
        List<PassageResponse> passages,
        Instant asOf) {
}
