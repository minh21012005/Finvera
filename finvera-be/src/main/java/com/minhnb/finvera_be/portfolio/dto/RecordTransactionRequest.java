package com.minhnb.finvera_be.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RecordTransactionRequest(
        @NotBlank String transactionType,
        String instrumentSymbol,
        String quantity,
        String price,
        String fee,
        String amount,
        @NotNull Instant executedAt) {
}
