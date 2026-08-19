package com.minhnb.finvera_be.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidTransactionRequest(
        @NotBlank @Size(min = 1, max = 200) String reason) {
}
