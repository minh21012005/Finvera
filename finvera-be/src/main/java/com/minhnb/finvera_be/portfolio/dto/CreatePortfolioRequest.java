package com.minhnb.finvera_be.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePortfolioRequest(
        @NotBlank @Size(max = 120) String name) {
}
