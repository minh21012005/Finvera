package com.minhnb.finvera_be.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddWatchlistItemRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9]{3,10}$") String symbol) {
}
