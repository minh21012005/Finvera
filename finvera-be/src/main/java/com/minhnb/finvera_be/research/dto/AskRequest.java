package com.minhnb.finvera_be.research.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(
        @NotBlank(message = "Query must not be blank")
        @Size(max = 2000, message = "Query must not exceed 2000 characters")
        String query,
        RetrieveFilter filters) {
}
