package com.minhnb.finvera_be.market.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.controller.OwnerAccessController;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.market.service.MarketOverviewService;
import com.minhnb.finvera_be.market.service.BreadthService;
import com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.FactorDirection;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel;
import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV1;
import com.minhnb.finvera_be.market.domain.regime.RegimeAssessment;
import com.minhnb.finvera_be.market.service.RegimeAssessmentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.json.JsonMapper;

/** Contract-first tests for FR-001, FR-002, FR-004, FR-005 and SEC-001/002/006. */
@WebMvcTest(controllers = {OwnerAccessController.class, MarketOverviewController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class})
class MarketOverviewControllerTests {

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
    private MarketOverviewService overviewService;

    @Test
    void overviewRequiresThePrivateOwnerSession() throws Exception {
        mvc.perform(get("/api/v1/market/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void authenticatedNonOwnerIsDenied() throws Exception {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                "other-" + UUID.randomUUID(), "N/A",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        mvc.perform(get("/api/v1/market/overview").session(session))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void ownerReceivesADegradedOverviewWithNullableDecimalStringsAndEtag() throws Exception {
        given(overviewService.latest()).willReturn(MarketOverviewService.empty(Instant.parse("2026-08-17T03:00:00Z")));
        MockHttpSession session = ownerSession();

        var response = mvc.perform(get("/api/v1/market/overview").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", containsString("\"")))
                .andExpect(jsonPath("$.contractVersion").value("1.0"))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.indices.length()").value(4))
                .andExpect(jsonPath("$.indices[3].dataStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.indices[3].value").value(nullValue()))
                .andExpect(jsonPath("$.indices[3].absoluteChange").value(nullValue()))
                .andExpect(jsonPath("$.indices[3].percentageChange").value(nullValue()))
                .andReturn();

        mvc.perform(get("/api/v1/market/overview")
                        .session(session)
                        .header("If-None-Match", response.getResponse().getHeader("ETag")))
                .andExpect(status().isNotModified());
    }

    @Test
    void ownerReceivesPartialBreadthWithItsReproducibleUniverseVersion() throws Exception {
        Instant now = Instant.parse("2026-08-17T03:00:00Z");
        var empty = MarketOverviewService.empty(now);
        var breadth = new BreadthService.Snapshot(UUID.randomUUID(), empty.indices().tradingDate(), now, DataStatus.PARTIAL,
                new BreadthCalculator.Result(3, 2, 1, 1, 7, java.util.List.of("MISSING_REFERENCE_PRICE")),
                "breadth-universe-v1", "a".repeat(64));
        given(overviewService.latest()).willReturn(new MarketOverviewService.MarketOverview(
                now, empty.indices(), breadth, null, DataStatus.PARTIAL, "market-breadth-fixture"));

        mvc.perform(get("/api/v1/market/overview").session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.breadth.dataStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.breadth.eligible").value(7))
                .andExpect(jsonPath("$.breadth.unclassified").value(1))
                .andExpect(jsonPath("$.breadth.universeVersion").value("breadth-universe-v1"))
                .andExpect(jsonPath("$.breadth.reasonCodes[0]").value("MISSING_REFERENCE_PRICE"));
    }

    @Test
    void ownerReceivesPersistedRegimeFactsAndSupportingFactorsWithoutBrowserCalculation() throws Exception {
        Instant now = Instant.parse("2026-08-17T03:00:00Z");
        var empty = MarketOverviewService.empty(now);
        var assessment = new RegimeAssessment(DataStatus.CURRENT, RegimeLabel.BULL, 80, 90,
                decimal("100"), decimal("100"), decimal("100"), false, java.util.List.of(), java.util.List.of(
                new RegimeAssessment.SupportingFactor(MarketRegimeV1.Component.TREND, FactorDirection.POSITIVE,
                        decimal("100"), decimal("0.350000"), decimal("0.350000"), decimal("35"))));
        var regime = new RegimeAssessmentService.Snapshot(empty.indices().tradingDate(), now,
                MarketRegimeV1.RULE_VERSION, assessment);
        given(overviewService.latest()).willReturn(new MarketOverviewService.MarketOverview(
                now, empty.indices(), null, regime, DataStatus.CURRENT, "market-regime-fixture"));

        mvc.perform(get("/api/v1/market/overview").session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regime.dataStatus").value("CURRENT"))
                .andExpect(jsonPath("$.regime.label").value("BULL"))
                .andExpect(jsonPath("$.regime.score").value(80))
                .andExpect(jsonPath("$.regime.confidence").value(90))
                .andExpect(jsonPath("$.regime.factors[0].code").value("TREND"))
                .andExpect(jsonPath("$.regime.factors[0].effectiveWeight").value("0.350000"));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
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
