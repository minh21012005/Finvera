package com.minhnb.finvera_be.stock.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.controller.OwnerAccessController;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.ScreenCriteria;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.CategoryDisclosure;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.ScreenExecutionResult;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.ScreenMatch;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.SortDirection;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.SortField;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** T008: contract/security tests for `POST /api/v1/screener/executions` (screener-v1.md, stock-screener.openapi.yaml). */
@WebMvcTest(controllers = {OwnerAccessController.class, ScreenerController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class})
class ScreenerControllerTests {

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
    private ScreenerService screenerService;

    @Test
    void executeRequiresThePrivateOwnerSession() throws Exception {
        mvc.perform(post("/api/v1/screener/executions").with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void ownerReceivesTheMatchListAndCategoryDisclosures() throws Exception {
        var match = new ScreenMatch("FPT", "FPT Corp", "HOSE", "Technology", Map.of("price", "123456.000000"),
                DataStatus.CURRENT, LocalDate.of(2026, 8, 14));
        var disclosure = new CategoryDisclosure("PRICE", DataStatus.CURRENT, null, 0);
        var result = new ScreenExecutionResult(List.of(match), 1, List.of(disclosure), "coh-screen-1",
                Instant.parse("2026-08-19T07:00:00Z"));
        given(screenerService.execute(any(ScreenCriteria.class), any(SortField.class), any(SortDirection.class),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .willReturn(result);

        mvc.perform(post("/api/v1/screener/executions").with(csrf()).session(ownerSession())
                        .contentType("application/json")
                        .content("""
                                {"price":{"priceMin":"100000","priceMax":"150000"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleVersion").value("screener-v1"))
                .andExpect(jsonPath("$.matches.length()").value(1))
                .andExpect(jsonPath("$.matches[0].symbol").value("FPT"))
                .andExpect(jsonPath("$.totalMatchCount").value(1))
                .andExpect(jsonPath("$.categoryDisclosures[0].category").value("PRICE"))
                .andExpect(jsonPath("$.coherenceKey").value("coh-screen-1"));
    }

    @Test
    void anEmptyRequestBodyStillExecutesAgainstTheFullUniverse() throws Exception {
        var result = new ScreenExecutionResult(List.of(), 0, List.of(), "coh-empty",
                Instant.parse("2026-08-19T07:00:00Z"));
        given(screenerService.execute(any(ScreenCriteria.class), any(SortField.class), any(SortDirection.class),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .willReturn(result);

        mvc.perform(post("/api/v1/screener/executions").with(csrf()).session(ownerSession())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMatchCount").value(0));
    }

    @Test
    void aContradictoryFilterRangeIsRejectedWithProblemDetails() throws Exception {
        given(screenerService.execute(any(ScreenCriteria.class), any(SortField.class), any(SortDirection.class),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .willThrow(new IllegalArgumentException("INVALID_FILTER_RANGE"));

        mvc.perform(post("/api/v1/screener/executions").with(csrf()).session(ownerSession())
                        .contentType("application/json")
                        .content("""
                                {"price":{"priceMin":"999","priceMax":"1"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.reasonCode").value("INVALID_FILTER_RANGE"));
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
