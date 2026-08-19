package com.minhnb.finvera_be.portfolio.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.controller.OwnerAccessController;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.portfolio.service.PortfolioAnalyticsService;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PortfolioNotFoundException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.WatchlistNotFoundException;
import com.minhnb.finvera_be.portfolio.service.PortfolioService;
import com.minhnb.finvera_be.portfolio.service.PositionService;
import com.minhnb.finvera_be.portfolio.service.TransactionService;
import com.minhnb.finvera_be.portfolio.service.WatchlistService;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = {
        OwnerAccessController.class,
        PortfolioController.class,
        TransactionController.class,
        WatchlistController.class,
        PortfolioAnalyticsController.class
})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class, ProblemDetailsAdvice.class})
class PortfolioSecurityTests {

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

    @MockitoBean
    private WatchlistService watchlistService;

    @MockitoBean
    private PortfolioAnalyticsService analyticsService;

    @Test
    @DisplayName("SEC-001/SEC-002: Accessing another owner's portfolio returns 404 PORTFOLIO_NOT_FOUND")
    void crossOwnerPortfolioReturns404() throws Exception {
        UUID otherOwnerPfId = UUID.randomUUID();
        given(portfolioService.getPortfolio(otherOwnerPfId))
                .willThrow(new PortfolioNotFoundException(otherOwnerPfId));

        mvc.perform(get("/api/v1/portfolios/" + otherOwnerPfId)
                        .session(ownerSession()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reasonCode").value("PORTFOLIO_NOT_FOUND"));
    }

    @Test
    @DisplayName("SEC-001/SEC-002: Accessing another owner's watchlist returns 404 WATCHLIST_NOT_FOUND")
    void crossOwnerWatchlistReturns404() throws Exception {
        UUID otherOwnerWlId = UUID.randomUUID();
        given(watchlistService.getWatchlist(otherOwnerWlId))
                .willThrow(new WatchlistNotFoundException(otherOwnerWlId));

        mvc.perform(get("/api/v1/watchlists/" + otherOwnerWlId)
                        .session(ownerSession()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reasonCode").value("WATCHLIST_NOT_FOUND"));
    }

    @Test
    @DisplayName("SEC-001/SEC-002: Modifying another owner's portfolio returns 404 PORTFOLIO_NOT_FOUND")
    void crossOwnerPortfolioModifyReturns404() throws Exception {
        UUID otherOwnerPfId = UUID.randomUUID();
        given(portfolioService.renamePortfolio(eq(otherOwnerPfId), any()))
                .willThrow(new PortfolioNotFoundException(otherOwnerPfId));

        mvc.perform(patch("/api/v1/portfolios/" + otherOwnerPfId)
                        .session(ownerSession())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("name", "Hacked"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reasonCode").value("PORTFOLIO_NOT_FOUND"));
    }

    @Test
    @DisplayName("SEC-001/SEC-002: Deleting another owner's watchlist returns 404 WATCHLIST_NOT_FOUND")
    void crossOwnerWatchlistDeleteReturns404() throws Exception {
        UUID otherOwnerWlId = UUID.randomUUID();
        org.mockito.BDDMockito.willThrow(new WatchlistNotFoundException(otherOwnerWlId))
                .given(watchlistService).deleteWatchlist(otherOwnerWlId);

        mvc.perform(delete("/api/v1/watchlists/" + otherOwnerWlId)
                        .session(ownerSession())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reasonCode").value("WATCHLIST_NOT_FOUND"));
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
