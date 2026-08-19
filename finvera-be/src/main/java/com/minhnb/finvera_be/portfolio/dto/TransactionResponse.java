package com.minhnb.finvera_be.portfolio.dto;

import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID portfolioId,
        Long sequenceNo,
        String transactionType,
        String instrumentSymbol,
        String quantity,
        String price,
        String fee,
        String amount,
        String currency,
        Instant executedAt,
        Instant entryAt,
        UUID voidsTransactionId,
        String voidReason,
        String idempotencyKey) {
}
