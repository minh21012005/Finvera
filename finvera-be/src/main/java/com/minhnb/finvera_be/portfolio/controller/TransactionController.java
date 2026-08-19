package com.minhnb.finvera_be.portfolio.controller;

import com.minhnb.finvera_be.portfolio.dto.RecordTransactionRequest;
import com.minhnb.finvera_be.portfolio.dto.TransactionPageResponse;
import com.minhnb.finvera_be.portfolio.dto.TransactionResponse;
import com.minhnb.finvera_be.portfolio.dto.VoidTransactionRequest;
import com.minhnb.finvera_be.portfolio.service.TransactionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for portfolio transaction ledger operations.
 * Path: {@code /api/v1/portfolios/{portfolioId}/transactions} per contracts/portfolio-watchlist.openapi.yaml.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public TransactionPageResponse listTransactions(
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return transactionService.listTransactions(portfolioId, limit, offset);
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> recordTransaction(
            @PathVariable UUID portfolioId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody RecordTransactionRequest request) {
        TransactionResponse response = transactionService.recordTransaction(portfolioId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{transactionId}/void")
    public ResponseEntity<TransactionResponse> voidTransaction(
            @PathVariable UUID portfolioId,
            @PathVariable UUID transactionId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody VoidTransactionRequest request) {
        TransactionResponse response = transactionService.voidTransaction(portfolioId, transactionId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
