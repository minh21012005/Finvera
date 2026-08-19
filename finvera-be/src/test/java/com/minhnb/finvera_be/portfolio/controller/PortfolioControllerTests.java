package com.minhnb.finvera_be.portfolio.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.controller.OwnerAccessController;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioValidationException;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioValidationException.ReasonCode;
import com.minhnb.finvera_be.portfolio.dto.CreatePortfolioRequest;
import com.minhnb.finvera_be.portfolio.dto.PortfolioSummaryResponse;
import com.minhnb.finvera_be.portfolio.dto.PositionResponse;
import com.minhnb.finvera_be.portfolio.dto.PositionsResponse;
import com.minhnb.finvera_be.portfolio.dto.RecordTransactionRequest;
import com.minhnb.finvera_be.portfolio.dto.RenamePortfolioRequest;
import com.minhnb.finvera_be.portfolio.dto.TransactionPageResponse;
import com.minhnb.finvera_be.portfolio.dto.TransactionResponse;
import com.minhnb.finvera_be.portfolio.dto.VoidTransactionRequest;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.DuplicatePortfolioNameException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.DuplicateSubmissionException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PortfolioNotFoundException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.UnsupportedInstrumentException;
import com.minhnb.finvera_be.portfolio.service.PortfolioService;
import com.minhnb.finvera_be.portfolio.service.PositionService;
import com.minhnb.finvera_be.portfolio.service.TransactionService;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = {OwnerAccessController.class, PortfolioController.class, TransactionController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class, ProblemDetailsAdvice.class})
class PortfolioControllerTests {

    private static final String OWNER_NAME = "owner-" + UUID.randomUUID();
    private static final String LOGIN_PROOF = UUID.randomUUID().toString();
    private static final String LOGIN_PROOF_HASH = new BCryptPasswordEncoder(4).encode(LOGIN_PROOF);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @DynamicPropertySource
    static void ownerProperties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", () -> "00000000-0000-0000-0000-000000000001");
        registry.add("finvera.security.owner.username", () -> OWNER_NAME);
        registry.add("finvera.security.owner.password-hash", () -> LOGIN_PROOF_HASH);
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PortfolioService portfolioService;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private PositionService positionService;

    @Test
    @DisplayName("GET /api/v1/portfolios requires authenticated owner session")
    void listPortfoliosRequiresAuth() throws Exception {
        mvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    @DisplayName("POST /api/v1/portfolios requires CSRF token even when authenticated")
    void createPortfolioRequiresCsrf() throws Exception {
        mvc.perform(post("/api/v1/portfolios")
                        .session(ownerSession())
                        .contentType("application/json")
                        .content("{\"name\":\"My Portfolio\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/portfolios creates portfolio and returns 201 Created")
    void createPortfolioSuccess() throws Exception {
        UUID pfId = UUID.randomUUID();
        PortfolioSummaryResponse response = new PortfolioSummaryResponse(
                pfId, "Growth", Instant.parse("2026-08-15T10:00:00Z"), "0", "0", "0", "0", Instant.parse("2026-08-15T10:00:00Z"));
        given(portfolioService.createPortfolio(any(CreatePortfolioRequest.class))).willReturn(response);

        mvc.perform(post("/api/v1/portfolios")
                        .with(csrf())
                        .session(ownerSession())
                        .contentType("application/json")
                        .content("{\"name\":\"Growth\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(pfId.toString()))
                .andExpect(jsonPath("$.name").value("Growth"))
                .andExpect(jsonPath("$.totalValue").value("0"));
    }

    @Test
    @DisplayName("POST /api/v1/portfolios with duplicate name returns 409 Conflict with DUPLICATE_PORTFOLIO_NAME")
    void createDuplicatePortfolioNameReturns409() throws Exception {
        given(portfolioService.createPortfolio(any(CreatePortfolioRequest.class)))
                .willThrow(new DuplicatePortfolioNameException("Existing"));

        mvc.perform(post("/api/v1/portfolios")
                        .with(csrf())
                        .session(ownerSession())
                        .contentType("application/json")
                        .content("{\"name\":\"Existing\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.reasonCode").value("DUPLICATE_PORTFOLIO_NAME"));
    }

    @Test
    @DisplayName("GET /api/v1/portfolios/{id} with unknown or wrong owner returns 404 PORTFOLIO_NOT_FOUND")
    void getUnknownPortfolioReturns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        given(portfolioService.getPortfolio(unknownId)).willThrow(new PortfolioNotFoundException(unknownId));

        mvc.perform(get("/api/v1/portfolios/" + unknownId)
                        .session(ownerSession()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.reasonCode").value("PORTFOLIO_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/portfolios/{id}/transactions missing Idempotency-Key header returns 400 Bad Request")
    void recordTransactionMissingIdempotencyKeyReturns400() throws Exception {
        UUID pfId = UUID.randomUUID();

        mvc.perform(post("/api/v1/portfolios/" + pfId + "/transactions")
                        .with(csrf())
                        .session(ownerSession())
                        .contentType("application/json")
                        .content("""
                                {"transactionType":"DEPOSIT","amount":"50000000","executedAt":"2026-08-01T10:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    @DisplayName("POST /api/v1/portfolios/{id}/transactions with unsupported symbol returns 400 UNSUPPORTED_INSTRUMENT")
    void recordTransactionUnsupportedSymbolReturns400() throws Exception {
        UUID pfId = UUID.randomUUID();
        given(transactionService.recordTransaction(eq(pfId), eq("key-1"), any(RecordTransactionRequest.class)))
                .willThrow(new UnsupportedInstrumentException("UNKNOWN"));

        mvc.perform(post("/api/v1/portfolios/" + pfId + "/transactions")
                        .header("Idempotency-Key", "key-1")
                        .with(csrf())
                        .session(ownerSession())
                        .contentType("application/json")
                        .content("""
                                {"transactionType":"BUY","instrumentSymbol":"UNKNOWN","quantity":"100","price":"50000","executedAt":"2026-08-01T10:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.reasonCode").value("UNSUPPORTED_INSTRUMENT"));
    }

    @Test
    @DisplayName("POST /api/v1/portfolios/{id}/transactions with insufficient position returns 409 INSUFFICIENT_POSITION")
    void recordTransactionInsufficientPositionReturns409() throws Exception {
        UUID pfId = UUID.randomUUID();
        given(transactionService.recordTransaction(eq(pfId), eq("key-2"), any(RecordTransactionRequest.class)))
                .willThrow(new PortfolioValidationException(ReasonCode.INSUFFICIENT_POSITION, "Not enough shares"));

        mvc.perform(post("/api/v1/portfolios/" + pfId + "/transactions")
                        .header("Idempotency-Key", "key-2")
                        .with(csrf())
                        .session(ownerSession())
                        .contentType("application/json")
                        .content("""
                                {"transactionType":"SELL","instrumentSymbol":"FPT","quantity":"1000","price":"50000","executedAt":"2026-08-01T10:00:00Z"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.reasonCode").value("INSUFFICIENT_POSITION"));
    }

    @Test
    @DisplayName("POST /api/v1/portfolios/{id}/transactions with duplicate idempotency key returns 409 DUPLICATE_SUBMISSION")
    void recordTransactionDuplicateKeyReturns409() throws Exception {
        UUID pfId = UUID.randomUUID();
        UUID originalTxId = UUID.randomUUID();
        given(transactionService.recordTransaction(eq(pfId), eq("dup-key"), any(RecordTransactionRequest.class)))
                .willThrow(new DuplicateSubmissionException("dup-key", originalTxId));

        mvc.perform(post("/api/v1/portfolios/" + pfId + "/transactions")
                        .header("Idempotency-Key", "dup-key")
                        .with(csrf())
                        .session(ownerSession())
                        .contentType("application/json")
                        .content("""
                                {"transactionType":"DEPOSIT","amount":"10000000","executedAt":"2026-08-01T10:00:00Z"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.reasonCode").value("DUPLICATE_SUBMISSION"));
    }

    @Test
    @DisplayName("POST /api/v1/portfolios/{id}/transactions/{id}/void voids transaction successfully")
    void voidTransactionSuccess() throws Exception {
        UUID pfId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        UUID voidTxId = UUID.randomUUID();

        TransactionResponse response = new TransactionResponse(
                voidTxId, pfId, 5L, "VOID", null, null, null, "0", null, "VND",
                Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-02T10:00:00Z"),
                txId, "Mistake", "void-key-1");
        given(transactionService.voidTransaction(eq(pfId), eq(txId), eq("void-key-1"), any(VoidTransactionRequest.class)))
                .willReturn(response);

        mvc.perform(post("/api/v1/portfolios/" + pfId + "/transactions/" + txId + "/void")
                        .header("Idempotency-Key", "void-key-1")
                        .with(csrf())
                        .session(ownerSession())
                        .contentType("application/json")
                        .content("{\"reason\":\"Mistake\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionType").value("VOID"))
                .andExpect(jsonPath("$.voidsTransactionId").value(txId.toString()))
                .andExpect(jsonPath("$.voidReason").value("Mistake"));
    }

    @Test
    @DisplayName("GET /api/v1/portfolios/{id}/positions returns 200 PositionsResponse")
    void getPositionsSuccess() throws Exception {
        UUID pfId = UUID.randomUUID();
        PositionResponse pos = new PositionResponse(
                "FPT", "1000", "50000", "60000", "DEFINED", "10000000", "0", "0.6");
        PositionsResponse response = new PositionsResponse(
                List.of(pos), "40000000", "100000000", "coh-123", Instant.parse("2026-08-15T10:00:00Z"));

        given(positionService.getPositions(pfId)).willReturn(response);

        mvc.perform(get("/api/v1/portfolios/" + pfId + "/positions")
                        .session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").value("100000000"))
                .andExpect(jsonPath("$.cashBalance").value("40000000"))
                .andExpect(jsonPath("$.positions[0].instrumentSymbol").value("FPT"))
                .andExpect(jsonPath("$.positions[0].quantity").value("1000"))
                .andExpect(jsonPath("$.coherenceKey").value("coh-123"));
    }

    private MockHttpSession ownerSession() throws Exception {
        var login = mvc.perform(post("/api/v1/auth/session")
                        .with(csrf())
                        .contentType("application/json")
                        .content(JSON.writeValueAsString(Map.of(
                                "username", OWNER_NAME,
                                "password", LOGIN_PROOF))))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }
}
