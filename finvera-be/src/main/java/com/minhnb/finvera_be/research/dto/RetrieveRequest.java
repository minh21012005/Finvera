package com.minhnb.finvera_be.research.dto;

public record RetrieveRequest(
        String query,
        RetrieveFilter filters) {
}
