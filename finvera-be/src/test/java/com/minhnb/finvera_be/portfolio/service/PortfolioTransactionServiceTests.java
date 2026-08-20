package com.minhnb.finvera_be.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.auth.config.OwnerProperties;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioValidationException;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioValidationException.ReasonCode;
import com.minhnb.finvera_be.portfolio.dto.CreatePortfolioRequest;
import com.minhnb.finvera_be.portfolio.dto.PortfolioSummaryResponse;
import com.minhnb.finvera_be.portfolio.dto.PositionsResponse;
import com.minhnb.finvera_be.portfolio.dto.RecordTransactionRequest;
import com.minhnb.finvera_be.portfolio.dto.RenamePortfolioRequest;
import com.minhnb.finvera_be.portfolio.dto.TransactionResponse;
import com.minhnb.finvera_be.portfolio.dto.VoidTransactionRequest;
import com.minhnb.finvera_be.portfolio.entity.PortfolioEntity;
import com.minhnb.finvera_be.portfolio.entity.PortfolioTransactionEntity;
import com.minhnb.finvera_be.portfolio.repository.PortfolioRepository;
import com.minhnb.finvera_be.portfolio.repository.PortfolioTransactionRepository;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.DuplicatePortfolioNameException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.DuplicateSubmissionException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PortfolioNotFoundException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.UnsupportedInstrumentException;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService.DailyBarReference;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortfolioTransactionServiceTests {

    private final UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID fptId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-15T10:00:00Z"), ZoneOffset.UTC);

    private PortfolioRepository portfolioRepository;
    private PortfolioTransactionRepository transactionRepository;
    private MarketReferenceDataService marketReferenceData;
    private StockReferenceDataService stockReferenceData;
    private OwnerScopedAccess ownerScopedAccess;
    private PositionService positionService;
    private PortfolioService portfolioService;
    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        portfolioRepository = mock(PortfolioRepository.class);
        transactionRepository = mock(PortfolioTransactionRepository.class);
        marketReferenceData = mock(MarketReferenceDataService.class);
        stockReferenceData = mock(StockReferenceDataService.class);
        ownerScopedAccess = new OwnerScopedAccess(new OwnerProperties(ownerId, "owner", "hash"));

        positionService = new PositionService(
                portfolioRepository,
                transactionRepository,
                marketReferenceData,
                stockReferenceData,
                ownerScopedAccess,
                clock);

        portfolioService = new PortfolioService(
                portfolioRepository,
                ownerScopedAccess,
                positionService,
                clock);

        transactionService = new TransactionService(
                portfolioRepository,
                transactionRepository,
                marketReferenceData,
                ownerScopedAccess,
                clock);
    }

    @Test
    @DisplayName("Create portfolio succeeds and initializes empty totals")
    void createPortfolio() {
        when(portfolioRepository.existsByOwnerIdAndNameAndDeletedAtIsNull(ownerId, "Main Portfolio")).thenReturn(false);
        when(portfolioRepository.save(any(PortfolioEntity.class))).thenAnswer(i -> i.getArgument(0));

        PortfolioSummaryResponse summary = portfolioService.createPortfolio(new CreatePortfolioRequest("Main Portfolio"));

        assertThat(summary.name()).isEqualTo("Main Portfolio");
        assertThat(summary.totalValue()).isEqualTo("0");
        assertThat(summary.cashBalance()).isEqualTo("0");
    }

    @Test
    @DisplayName("Create portfolio with duplicate name throws DuplicatePortfolioNameException")
    void createDuplicatePortfolioNameThrows() {
        when(portfolioRepository.existsByOwnerIdAndNameAndDeletedAtIsNull(ownerId, "Duplicate")).thenReturn(true);

        assertThatThrownBy(() -> portfolioService.createPortfolio(new CreatePortfolioRequest("Duplicate")))
                .isInstanceOf(DuplicatePortfolioNameException.class);
    }

    @Test
    @DisplayName("Rename portfolio updates name")
    void renamePortfolio() {
        UUID pfId = UUID.randomUUID();
        PortfolioEntity entity = new PortfolioEntity(pfId, ownerId, "Old Name", Instant.now(clock), null);
        when(portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNull(pfId, ownerId)).thenReturn(Optional.of(entity));
        when(portfolioRepository.existsByOwnerIdAndNameAndDeletedAtIsNull(ownerId, "New Name")).thenReturn(false);
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(pfId)).thenReturn(Collections.emptyList());

        PortfolioSummaryResponse response = portfolioService.renamePortfolio(pfId, new RenamePortfolioRequest("New Name"));

        assertThat(response.name()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("Delete portfolio marks deletedAt")
    void deletePortfolio() {
        UUID pfId = UUID.randomUUID();
        PortfolioEntity entity = new PortfolioEntity(pfId, ownerId, "Delete Me", Instant.now(clock), null);
        when(portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNull(pfId, ownerId)).thenReturn(Optional.of(entity));

        portfolioService.deletePortfolio(pfId);

        assertThat(entity.isDeleted()).isTrue();
        verify(portfolioRepository).save(entity);
    }

    @Test
    @DisplayName("Record DEPOSIT and BUY transactions")
    void recordDepositAndBuy() {
        UUID pfId = UUID.randomUUID();
        PortfolioEntity entity = new PortfolioEntity(pfId, ownerId, "Trading", Instant.now(clock), null);
        when(portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNullForUpdate(pfId, ownerId)).thenReturn(Optional.of(entity));
        when(transactionRepository.findByPortfolioIdAndIdempotencyKey(eq(pfId), any())).thenReturn(Optional.empty());
        when(transactionRepository.save(any(PortfolioTransactionEntity.class))).thenAnswer(i -> i.getArgument(0));

        // 1. Record deposit 50,000,000
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(pfId)).thenReturn(Collections.emptyList());
        RecordTransactionRequest depReq = new RecordTransactionRequest(
                "DEPOSIT", null, null, null, "0", "50000000", Instant.parse("2026-08-01T10:00:00Z"));
        TransactionResponse depRes = transactionService.recordTransaction(pfId, "dep-key-1", depReq);
        assertThat(depRes.transactionType()).isEqualTo("DEPOSIT");
        assertThat(depRes.amount()).isEqualTo("50000000");

        // 2. Record BUY FPT
        InstrumentReference fptRef = new InstrumentReference(fptId, "HOSE", "FPT", "EQUITY", "ACTIVE");
        when(marketReferenceData.findActiveInstrumentBySymbol("FPT")).thenReturn(Optional.of(fptRef));

        PortfolioTransactionEntity depEntity = new PortfolioTransactionEntity(
                UUID.randomUUID(), pfId, "dep-key-1", "DEPOSIT", null, null, null, BigDecimal.ZERO,
                new BigDecimal("50000000"), "VND", Instant.parse("2026-08-01T10:00:00Z"), Instant.now(clock), null, null);
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(pfId)).thenReturn(List.of(depEntity));

        RecordTransactionRequest buyReq = new RecordTransactionRequest(
                "BUY", "FPT", "100", "50000", "5000", null, Instant.parse("2026-08-02T10:00:00Z"));
        TransactionResponse buyRes = transactionService.recordTransaction(pfId, "buy-key-1", buyReq);
        assertThat(buyRes.transactionType()).isEqualTo("BUY");
        assertThat(buyRes.instrumentSymbol()).isEqualTo("FPT");
        assertThat(buyRes.quantity()).isEqualTo("100");
    }

    @Test
    @DisplayName("Record BUY with unsupported symbol throws UnsupportedInstrumentException")
    void recordBuyUnsupportedSymbol() {
        UUID pfId = UUID.randomUUID();
        when(portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNullForUpdate(pfId, ownerId))
                .thenReturn(Optional.of(new PortfolioEntity(pfId, ownerId, "PF", Instant.now(clock), null)));
        when(marketReferenceData.findActiveInstrumentBySymbol("INVALID")).thenReturn(Optional.empty());

        RecordTransactionRequest req = new RecordTransactionRequest(
                "BUY", "INVALID", "100", "50000", "0", null, Instant.now(clock));

        assertThatThrownBy(() -> transactionService.recordTransaction(pfId, "key-bad-sym", req))
                .isInstanceOf(UnsupportedInstrumentException.class);
    }

    @Test
    @DisplayName("Replaying same Idempotency-Key on same portfolio throws DuplicateSubmissionException")
    void replayedIdempotencyKeyThrowsDuplicateSubmission() {
        UUID pfId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        when(portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNullForUpdate(pfId, ownerId))
                .thenReturn(Optional.of(new PortfolioEntity(pfId, ownerId, "PF", Instant.now(clock), null)));

        PortfolioTransactionEntity existing = new PortfolioTransactionEntity(
                txId, pfId, "idem-123", "DEPOSIT", null, null, null, BigDecimal.ZERO,
                new BigDecimal("10000000"), "VND", Instant.now(clock), Instant.now(clock), null, null);
        when(transactionRepository.findByPortfolioIdAndIdempotencyKey(pfId, "idem-123")).thenReturn(Optional.of(existing));

        RecordTransactionRequest req = new RecordTransactionRequest(
                "DEPOSIT", null, null, null, "0", "10000000", Instant.now(clock));

        assertThatThrownBy(() -> transactionService.recordTransaction(pfId, "idem-123", req))
                .isInstanceOf(DuplicateSubmissionException.class)
                .satisfies(e -> assertThat(((DuplicateSubmissionException) e).getExistingTransactionId()).isEqualTo(txId));
    }

    @Test
    @DisplayName("VOID of valid transaction succeeds")
    void voidValidTransaction() {
        UUID pfId = UUID.randomUUID();
        UUID depId = UUID.randomUUID();
        when(portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNullForUpdate(pfId, ownerId))
                .thenReturn(Optional.of(new PortfolioEntity(pfId, ownerId, "PF", Instant.now(clock), null)));

        PortfolioTransactionEntity depEntity = new PortfolioTransactionEntity(
                depId, pfId, "dep-1", "DEPOSIT", null, null, null, BigDecimal.ZERO,
                new BigDecimal("10000000"), "VND", Instant.parse("2026-08-01T10:00:00Z"), Instant.now(clock), null, null);

        when(transactionRepository.findByPortfolioIdAndIdempotencyKey(pfId, "void-key-1")).thenReturn(Optional.empty());
        when(transactionRepository.findByIdAndPortfolioId(depId, pfId)).thenReturn(Optional.of(depEntity));
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(pfId)).thenReturn(List.of(depEntity));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransactionResponse voidRes = transactionService.voidTransaction(
                pfId, depId, "void-key-1", new VoidTransactionRequest("Mistake"));

        assertThat(voidRes.transactionType()).isEqualTo("VOID");
        assertThat(voidRes.voidsTransactionId()).isEqualTo(depId);
        assertThat(voidRes.voidReason()).isEqualTo("Mistake");
    }

    @Test
    @DisplayName("PositionService computes holdings and allocation on demand")
    void getPositionsOnDemand() {
        UUID pfId = UUID.randomUUID();
        when(portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNull(pfId, ownerId))
                .thenReturn(Optional.of(new PortfolioEntity(pfId, ownerId, "PF", Instant.now(clock), null)));

        PortfolioTransactionEntity dep = new PortfolioTransactionEntity(
                UUID.randomUUID(), pfId, "dep-1", "DEPOSIT", null, null, null, BigDecimal.ZERO,
                new BigDecimal("100000000"), "VND", Instant.parse("2026-08-01T10:00:00Z"), Instant.now(clock), null, null);

        PortfolioTransactionEntity buy = new PortfolioTransactionEntity(
                UUID.randomUUID(), pfId, "buy-1", "BUY", fptId, new BigDecimal("1000"),
                new BigDecimal("50000"), BigDecimal.ZERO, null, "VND", Instant.parse("2026-08-02T10:00:00Z"), Instant.now(clock), null, null);

        when(transactionRepository.findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(pfId)).thenReturn(List.of(dep, buy));
        when(marketReferenceData.findInstrumentsByIds(any())).thenReturn(List.of(
                new InstrumentReference(fptId, "HOSE", "FPT", "EQUITY", "ACTIVE")));

        DailyBarReference fptBar = new DailyBarReference(
                UUID.randomUUID(), fptId, LocalDate.parse("2026-08-14"), new BigDecimal("59000"),
                new BigDecimal("61000"), new BigDecimal("59000"), new BigDecimal("60000"),
                1000000L, new BigDecimal("60000000000"), "TCBS", Instant.now(clock));
        when(stockReferenceData.findLatestDailyBars(any())).thenReturn(List.of(fptBar));

        PositionsResponse positionsRes = positionService.getPositions(pfId);

        // Cash: 50,000,000, Stock: 60,000,000, Total: 110,000,000
        assertThat(positionsRes.cashBalance()).isEqualTo("50000000");
        assertThat(positionsRes.totalValue()).isEqualTo("110000000");
        assertThat(positionsRes.positions()).hasSize(1);

        var pos = positionsRes.positions().get(0);
        assertThat(pos.instrumentSymbol()).isEqualTo("FPT");
        assertThat(pos.quantity()).isEqualTo("1000");
        assertThat(pos.averageCostBasis()).isEqualTo("50000");
        assertThat(pos.currentPrice()).isEqualTo("60000");
        assertThat(pos.unrealizedPL()).isEqualTo("10000000");
        // Allocation: 60M / 110M = 0.545454545455
        assertThat(pos.allocation()).startsWith("0.5454");
    }
}
