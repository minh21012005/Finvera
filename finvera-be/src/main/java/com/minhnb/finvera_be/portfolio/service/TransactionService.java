package com.minhnb.finvera_be.portfolio.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1.TransactionInput;
import com.minhnb.finvera_be.portfolio.domain.model.PortfolioTypes.TransactionType;
import com.minhnb.finvera_be.portfolio.dto.RecordTransactionRequest;
import com.minhnb.finvera_be.portfolio.dto.TransactionPageResponse;
import com.minhnb.finvera_be.portfolio.dto.TransactionResponse;
import com.minhnb.finvera_be.portfolio.dto.VoidTransactionRequest;
import com.minhnb.finvera_be.portfolio.entity.PortfolioEntity;
import com.minhnb.finvera_be.portfolio.entity.PortfolioTransactionEntity;
import com.minhnb.finvera_be.portfolio.repository.PortfolioRepository;
import com.minhnb.finvera_be.portfolio.repository.PortfolioTransactionRepository;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.DuplicateSubmissionException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PortfolioNotFoundException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.TransactionNotFoundException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.UnsupportedInstrumentException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioTransactionRepository transactionRepository;
    private final MarketReferenceDataService marketReferenceData;
    private final OwnerScopedAccess ownerScopedAccess;
    private final Clock clock;

    public TransactionService(
            PortfolioRepository portfolioRepository,
            PortfolioTransactionRepository transactionRepository,
            MarketReferenceDataService marketReferenceData,
            OwnerScopedAccess ownerScopedAccess,
            Clock clock) {
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.marketReferenceData = marketReferenceData;
        this.ownerScopedAccess = ownerScopedAccess;
        this.clock = clock;
    }

    @Transactional
    public TransactionResponse recordTransaction(
            UUID portfolioId,
            String idempotencyKey,
            RecordTransactionRequest request) {
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(request, "request");

        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();
        // Row lock held for this whole method: serializes concurrent writes to
        // the same portfolio so two racing requests can't both validate against
        // the same pre-write ledger snapshot (see PortfolioRepository javadoc).
        portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNullForUpdate(portfolioId, ownerId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));

        // Check duplicate submission
        var existingTx = transactionRepository.findByPortfolioIdAndIdempotencyKey(portfolioId, idempotencyKey);
        if (existingTx.isPresent()) {
            throw new DuplicateSubmissionException(idempotencyKey, existingTx.get().getId());
        }

        TransactionType type = TransactionType.valueOf(request.transactionType().toUpperCase());
        UUID instrumentId = null;
        String symbol = null;
        BigDecimal quantity = null;
        BigDecimal price = null;
        BigDecimal fee = request.fee() != null && !request.fee().isBlank()
                ? new BigDecimal(request.fee())
                : BigDecimal.ZERO;
        BigDecimal amount = null;

        if (type == TransactionType.BUY || type == TransactionType.SELL) {
            if (request.instrumentSymbol() == null || request.instrumentSymbol().isBlank()) {
                throw new UnsupportedInstrumentException("MISSING_SYMBOL");
            }
            symbol = request.instrumentSymbol().trim().toUpperCase();
            InstrumentReference inst = marketReferenceData.findActiveInstrumentBySymbol(symbol)
                    .orElseThrow(() -> new UnsupportedInstrumentException(request.instrumentSymbol()));
            instrumentId = inst.instrumentId();

            if (request.quantity() == null || request.quantity().isBlank()) {
                throw new IllegalArgumentException("Quantity is required for BUY/SELL");
            }
            quantity = new BigDecimal(request.quantity());

            if (request.price() == null || request.price().isBlank()) {
                throw new IllegalArgumentException("Price is required for BUY/SELL");
            }
            price = new BigDecimal(request.price());
        } else if (type == TransactionType.DEPOSIT || type == TransactionType.WITHDRAW) {
            if (request.amount() == null || request.amount().isBlank()) {
                throw new IllegalArgumentException("Amount is required for DEPOSIT/WITHDRAW");
            }
            amount = new BigDecimal(request.amount());
        }

        // Validate ledger replay consistency
        List<TransactionInput> existingInputs = loadExistingTransactionInputs(portfolioId);

        UUID newTxId = UUID.randomUUID();
        long nextSeq = nextSequenceNo(portfolioId);
        TransactionInput newInput = new TransactionInput(
                newTxId,
                nextSeq,
                type,
                instrumentId,
                symbol,
                quantity,
                price,
                fee,
                amount,
                "VND",
                request.executedAt(),
                Instant.now(clock),
                null,
                null);

        PortfolioAnalyticsV1.validateTransaction(existingInputs, newInput);

        Instant entryAt = Instant.now(clock);
        PortfolioTransactionEntity entity = new PortfolioTransactionEntity(
                newTxId,
                portfolioId,
                idempotencyKey,
                type.name(),
                instrumentId,
                quantity,
                price,
                fee,
                amount,
                "VND",
                request.executedAt(),
                entryAt,
                null,
                null);

        PortfolioTransactionEntity saved = transactionRepository.save(entity);

        return toResponse(saved, symbol);
    }

    @Transactional
    public TransactionResponse voidTransaction(
            UUID portfolioId,
            UUID transactionId,
            String idempotencyKey,
            VoidTransactionRequest request) {
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(request, "request");

        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();
        portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNullForUpdate(portfolioId, ownerId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));

        var duplicate = transactionRepository.findByPortfolioIdAndIdempotencyKey(portfolioId, idempotencyKey);
        if (duplicate.isPresent()) {
            throw new DuplicateSubmissionException(idempotencyKey, duplicate.get().getId());
        }

        PortfolioTransactionEntity target = transactionRepository.findByIdAndPortfolioId(transactionId, portfolioId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        List<TransactionInput> existingInputs = loadExistingTransactionInputs(portfolioId);

        UUID voidTxId = UUID.randomUUID();
        long nextSeq = nextSequenceNo(portfolioId);
        TransactionInput voidInput = new TransactionInput(
                voidTxId,
                nextSeq,
                TransactionType.VOID,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                null,
                "VND",
                target.getExecutedAt(),
                Instant.now(clock),
                transactionId,
                request.reason().trim());

        PortfolioAnalyticsV1.validateTransaction(existingInputs, voidInput);

        Instant entryAt = Instant.now(clock);
        PortfolioTransactionEntity voidEntity = new PortfolioTransactionEntity(
                voidTxId,
                portfolioId,
                idempotencyKey,
                TransactionType.VOID.name(),
                null,
                null,
                null,
                BigDecimal.ZERO,
                null,
                "VND",
                target.getExecutedAt(),
                entryAt,
                transactionId,
                request.reason().trim());

        PortfolioTransactionEntity saved = transactionRepository.save(voidEntity);

        return toResponse(saved, null);
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse listTransactions(UUID portfolioId, int limit, int offset) {
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();
        portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNull(portfolioId, ownerId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));

        List<PortfolioTransactionEntity> allTxs =
                transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(portfolioId);

        int totalCount = allTxs.size();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(0, offset);

        int fromIndex = Math.min(safeOffset, totalCount);
        int toIndex = Math.min(safeOffset + safeLimit, totalCount);
        List<PortfolioTransactionEntity> pageItems = allTxs.subList(fromIndex, toIndex);

        Set<UUID> instrumentIds = pageItems.stream()
                .map(PortfolioTransactionEntity::getInstrumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> symbols = new HashMap<>();
        if (!instrumentIds.isEmpty()) {
            List<InstrumentReference> instruments = marketReferenceData.findInstrumentsByIds(instrumentIds);
            for (InstrumentReference inst : instruments) {
                symbols.put(inst.instrumentId(), inst.symbol());
            }
        }

        List<TransactionResponse> responses = pageItems.stream()
                .map(tx -> toResponse(tx, symbols.get(tx.getInstrumentId())))
                .toList();

        return new TransactionPageResponse(responses, totalCount, safeLimit, safeOffset);
    }

    private List<TransactionInput> loadExistingTransactionInputs(UUID portfolioId) {
        return transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(portfolioId)
                .stream()
                .map(tx -> PositionService.toTransactionInput(tx, null))
                .toList();
    }

    /**
     * A preview sequence number for validation input only (the real value is
     * DB-assigned via {@code sequence_no BIGSERIAL}, {@code insertable=false}
     * on the entity, and discarded on insert). MUST be derived from the true
     * max {@code sequence_no} in this portfolio, not the last element of a
     * list sorted by {@code executed_at} — a backdated transaction can sort
     * before a later-sequenced row, which previously made this collide with
     * an already-used sequence number and corrupted same-timestamp tie-break
     * validation (contract U-3).
     */
    private long nextSequenceNo(UUID portfolioId) {
        return transactionRepository.findFirstByPortfolioIdOrderBySequenceNoDesc(portfolioId)
                .map(tx -> tx.getSequenceNo() != null ? tx.getSequenceNo() + 1 : 1L)
                .orElse(1L);
    }

    private static TransactionResponse toResponse(PortfolioTransactionEntity entity, String symbol) {
        return new TransactionResponse(
                entity.getId(),
                entity.getPortfolioId(),
                entity.getSequenceNo(),
                entity.getTransactionType(),
                symbol,
                entity.getQuantity() != null ? entity.getQuantity().toPlainString() : null,
                entity.getPrice() != null ? entity.getPrice().toPlainString() : null,
                entity.getFee() != null ? entity.getFee().toPlainString() : "0",
                entity.getAmount() != null ? entity.getAmount().toPlainString() : null,
                entity.getCurrency(),
                entity.getExecutedAt(),
                entity.getEntryAt(),
                entity.getVoidsTransactionId(),
                entity.getVoidReason(),
                entity.getIdempotencyKey());
    }
}
