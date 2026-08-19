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
import com.minhnb.finvera_be.portfolio.dto.AddWatchlistItemRequest;
import com.minhnb.finvera_be.portfolio.dto.CreateWatchlistRequest;
import com.minhnb.finvera_be.portfolio.dto.RenameWatchlistRequest;
import com.minhnb.finvera_be.portfolio.dto.WatchlistDetailResponse;
import com.minhnb.finvera_be.portfolio.dto.WatchlistItemResponse;
import com.minhnb.finvera_be.portfolio.dto.WatchlistSummaryResponse;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.DuplicateWatchlistNameException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.UnsupportedInstrumentException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.WatchlistNotFoundException;
import com.minhnb.finvera_be.portfolio.service.WatchlistService;
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

@WebMvcTest(controllers = {OwnerAccessController.class, WatchlistController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class, ProblemDetailsAdvice.class})
class WatchlistControllerTests {

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
    private WatchlistService watchlistService;

    @Test
    @DisplayName("GET /api/v1/watchlists requires authenticated owner session")
    void listWatchlistsRequiresAuth() throws Exception {
        mvc.perform(get("/api/v1/watchlists"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    @DisplayName("POST /api/v1/watchlists requires CSRF token even when authenticated")
    void createWatchlistRequiresCsrf() throws Exception {
        mvc.perform(post("/api/v1/watchlists")
                        .session(ownerSession())
                        .contentType("application/json")
                        .content("{\"name\":\"Tech\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/watchlists creates watchlist and returns 201 Created")
    void createWatchlistSuccess() throws Exception {
        UUID wlId = UUID.randomUUID();
        WatchlistSummaryResponse response = new WatchlistSummaryResponse(
                wlId, "Tech", Instant.parse("2026-08-15T10:00:00Z"), 0);
        given(watchlistService.createWatchlist(any(CreateWatchlistRequest.class))).willReturn(response);

        mvc.perform(post("/api/v1/watchlists")
                        .with(csrf())
                        .session(ownerSession())
                        .contentType("application/json")
                        .content("{\"name\":\"Tech\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(wlId.toString()))
                .andExpect(jsonPath("$.name").value("Tech"))
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/watchlists with duplicate name returns 409 DUPLICATE_WATCHLIST_NAME")
    void createDuplicateWatchlistNameReturns409() throws Exception {
        given(watchlistService.createWatchlist(any(CreateWatchlistRequest.class)))
                .willThrow(new DuplicateWatchlistNameException("Existing"));

        mvc.perform(post("/api/v1/watchlists")
                        .with(csrf())
                        .session(ownerSession())
                        .contentType("application/json")
                        .content("{\"name\":\"Existing\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.reasonCode").value("DUPLICATE_WATCHLIST_NAME"));
    }

    @Test
    @DisplayName("GET /api/v1/watchlists/{id} returns 200 WatchlistDetailResponse")
    void getWatchlistSuccess() throws Exception {
        UUID wlId = UUID.randomUUID();
        WatchlistItemResponse item = new WatchlistItemResponse(
                "FPT", "Tập đoàn FPT", Instant.parse("2026-08-10T00:00:00Z"), "60000", "2.5",
                "BULLISH", "NORMAL", true, "BULLISH", "LOW", "CURRENT", null);
        WatchlistDetailResponse response = new WatchlistDetailResponse(
                wlId, "Main Watchlist", List.of(item), "coh-wl-1", Instant.parse("2026-08-15T10:00:00Z"));

        given(watchlistService.getWatchlist(wlId)).willReturn(response);

        mvc.perform(get("/api/v1/watchlists/" + wlId)
                        .session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(wlId.toString()))
                .andExpect(jsonPath("$.items[0].symbol").value("FPT"))
                .andExpect(jsonPath("$.items[0].currentPrice").value("60000"))
                .andExpect(jsonPath("$.items[0].hasCurrentSignal").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/watchlists/{id} with unknown ID returns 404 WATCHLIST_NOT_FOUND")
    void getUnknownWatchlistReturns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        given(watchlistService.getWatchlist(unknownId)).willThrow(new WatchlistNotFoundException(unknownId));

        mvc.perform(get("/api/v1/watchlists/" + unknownId)
                        .session(ownerSession()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.reasonCode").value("WATCHLIST_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/watchlists/{id}/items with unsupported symbol returns 400 UNSUPPORTED_INSTRUMENT")
    void addItemUnsupportedSymbolReturns400() throws Exception {
        UUID wlId = UUID.randomUUID();
        given(watchlistService.addItem(eq(wlId), any(AddWatchlistItemRequest.class)))
                .willThrow(new UnsupportedInstrumentException("INVALID"));

        mvc.perform(post("/api/v1/watchlists/" + wlId + "/items")
                        .with(csrf())
                        .session(ownerSession())
                        .contentType("application/json")
                        .content("{\"symbol\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.reasonCode").value("UNSUPPORTED_INSTRUMENT"));
    }

    @Test
    @DisplayName("DELETE /api/v1/watchlists/{id}/items/{symbol} returns 204 No Content")
    void removeItemSuccess() throws Exception {
        UUID wlId = UUID.randomUUID();

        mvc.perform(delete("/api/v1/watchlists/" + wlId + "/items/FPT")
                        .with(csrf())
                        .session(ownerSession()))
                .andExpect(status().isNoContent());
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
