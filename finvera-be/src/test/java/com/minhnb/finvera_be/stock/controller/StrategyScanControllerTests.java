package com.minhnb.finvera_be.stock.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.controller.OwnerAccessController;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.Direction;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.LevelSet;
import com.minhnb.finvera_be.stock.service.strategy.StrategyScanService;
import com.minhnb.finvera_be.stock.service.strategy.StrategyScanService.ScanMatch;
import com.minhnb.finvera_be.stock.service.strategy.StrategyScanService.ScanResult;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService.SignalDetail;
import java.math.BigDecimal;
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

/** T019: contract/security tests for `POST /api/v1/strategies/{strategyCode}/scan` (strategy-signal.openapi.yaml). */
@WebMvcTest(controllers = {OwnerAccessController.class, StrategyScanController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class})
class StrategyScanControllerTests {

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
    private StrategyScanService scanService;

    @Test
    void scanRequiresThePrivateOwnerSession() throws Exception {
        mvc.perform(post("/api/v1/strategies/TREND_FOLLOWING/scan").with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void scanRequiresACsrfTokenEvenWithAnAuthenticatedOwnerSession() throws Exception {
        // quickstart.md Authorization checks: verified from the first
        // implementation this time (Feature 003's T030 follow-up finding).
        mvc.perform(post("/api/v1/strategies/TREND_FOLLOWING/scan").session(ownerSession())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(scanService);
    }

    @Test
    void ownerReceivesTheMatchListAndExclusionCount() throws Exception {
        var levels = new LevelSet(new BigDecimal("99.500000"), new BigDecimal("100.500000"),
                new BigDecimal("96.000000"), new BigDecimal("108.000000"), new BigDecimal("112.000000"),
                new BigDecimal("2.0000"));
        var signal = new SignalDetail(StrategyCode.TREND_FOLLOWING, StrategySignalV1.RULE_VERSION, Direction.LONG,
                levels, 25, com.minhnb.finvera_be.stock.domain.model.StockTypes.RiskLevel.LOW,
                com.minhnb.finvera_be.stock.domain.model.StockTypes.SignalStrength.STRONG, List.of(),
                Map.of(), List.of(), LocalDate.of(2026, 8, 14), Instant.parse("2026-08-14T08:15:00Z"));
        var match = new ScanMatch("FPT", "CTCP FPT", "HOSE", signal);
        var result = new ScanResult(StrategyCode.TREND_FOLLOWING, List.of(match), 1, 50, 0, 3,
                Instant.parse("2026-08-14T08:15:01Z"));
        given(scanService.scan(any(StrategyCode.class), anyInt(), anyInt())).willReturn(result);

        mvc.perform(post("/api/v1/strategies/TREND_FOLLOWING/scan").with(csrf()).session(ownerSession())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyCode").value("TREND_FOLLOWING"))
                .andExpect(jsonPath("$.matches.length()").value(1))
                .andExpect(jsonPath("$.matches[0].symbol").value("FPT"))
                .andExpect(jsonPath("$.totalMatchCount").value(1))
                .andExpect(jsonPath("$.excludedForInsufficientHistoryCount").value(3));
    }

    @Test
    void anEmptyResultIsASpecificStateNotAnError() throws Exception {
        var result = new ScanResult(StrategyCode.MEAN_REVERSION, List.of(), 0, 50, 0, 0,
                Instant.parse("2026-08-14T08:15:01Z"));
        given(scanService.scan(any(StrategyCode.class), anyInt(), anyInt())).willReturn(result);

        mvc.perform(post("/api/v1/strategies/MEAN_REVERSION/scan").with(csrf()).session(ownerSession())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMatchCount").value(0))
                .andExpect(jsonPath("$.matches.length()").value(0));
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
