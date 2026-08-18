package com.minhnb.finvera_be.stock.controller;

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
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Direction;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.overview.StockOverviewCalculator.StockOverviewResult;
import com.minhnb.finvera_be.stock.service.StockChartService;
import com.minhnb.finvera_be.stock.service.StockOverviewService;
import com.minhnb.finvera_be.stock.service.StockOverviewService.StockOverview;
import com.minhnb.finvera_be.stock.service.StockSearchService;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Contract-first tests for FR-001, FR-002, FR-003, FR-013, FR-016 and SEC-001/002/004. */
@WebMvcTest(controllers = {OwnerAccessController.class, StockController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class})
class StockOverviewControllerTests {

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
    private StockSearchService searchService;

    @MockitoBean
    private StockOverviewService overviewService;

    @MockitoBean
    private StockChartService chartService;

    @MockitoBean
    private TechnicalIndicatorService technicalService;

    @MockitoBean
    private com.minhnb.finvera_be.stock.service.FundamentalReportService fundamentalService;

    @MockitoBean
    private com.minhnb.finvera_be.stock.service.ValuationService valuationService;

    @Test
    void overviewRequiresThePrivateOwnerSession() throws Exception {
        mvc.perform(get("/api/v1/stocks/FPT"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void authenticatedNonOwnerIsDenied() throws Exception {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                "other-" + UUID.randomUUID(), "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        mvc.perform(get("/api/v1/stocks/FPT").session(session))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void ownerReceivesACompleteOverviewWithNullableDecimalStringsAndCoherenceKeyEtag() throws Exception {
        given(overviewService.findBySymbol("FPT")).willReturn(Optional.of(complete()));

        var response = mvc.perform(get("/api/v1/stocks/FPT").session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", containsString("coh-fpt-1")))
                .andExpect(jsonPath("$.meta.contractVersion").value("1.0"))
                .andExpect(jsonPath("$.meta.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.profile.symbol").value("FPT"))
                .andExpect(jsonPath("$.price.last").value("123600.000000"))
                .andExpect(jsonPath("$.price.absoluteChange").value("1100.000000"))
                .andExpect(jsonPath("$.price.direction").value("UP"))
                .andReturn();

        mvc.perform(get("/api/v1/stocks/FPT")
                        .session((MockHttpSession) response.getRequest().getSession(false))
                        .header("If-None-Match", response.getResponse().getHeader("ETag")))
                .andExpect(status().isNotModified());
    }

    @Test
    void changeFieldsAreNullNotZeroWhenTheReferenceBasisIsMissing() throws Exception {
        given(overviewService.findBySymbol("FPT")).willReturn(Optional.of(missingReference()));

        mvc.perform(get("/api/v1/stocks/FPT").session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.last").value("123600.000000"))
                .andExpect(jsonPath("$.price.absoluteChange").value(nullValue()))
                .andExpect(jsonPath("$.price.percentageChange").value(nullValue()))
                .andExpect(jsonPath("$.price.applicability").value("MISSING"))
                .andExpect(jsonPath("$.price.changeBasisReason").value("REFERENCE_PRICE_UNAVAILABLE"));
    }

    @Test
    void unknownSymbolReturnsAStockNotSupportedProblemWithoutFabricatingAProfile() throws Exception {
        given(overviewService.findBySymbol("ZZZZZ")).willReturn(Optional.empty());

        mvc.perform(get("/api/v1/stocks/ZZZZZ").session(ownerSession()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.reasonCode").value("STOCK_NOT_SUPPORTED"));
    }

    private static StockOverview complete() {
        StockOverviewResult price = new StockOverviewResult(MetricApplicability.DEFINED,
                new BigDecimal("123600.000000"), new BigDecimal("122500.000000"), new BigDecimal("1100.000000"),
                new BigDecimal("0.897959"), Direction.UP, 2_270_000L, new BigDecimal("280457200000.0000"),
                new BigDecimal("180703200000000.000000"), null);
        return new StockOverview("FPT", "HOSE", "CTCP FPT", "FPT Corporation", "LISTED",
                "Information Technology", "finvera-sector-v1", 1_462_000_000L, price, SessionState.OPEN,
                LocalDate.of(2026, 8, 17), Instant.parse("2026-08-17T07:15:00Z"), DataStatus.CURRENT,
                List.of(), "coh-fpt-1");
    }

    private static StockOverview missingReference() {
        StockOverviewResult price = new StockOverviewResult(MetricApplicability.MISSING,
                new BigDecimal("123600.000000"), null, null, null, Direction.UNCHANGED, 2_270_000L,
                new BigDecimal("280457200000.0000"), null, "REFERENCE_PRICE_UNAVAILABLE");
        return new StockOverview("FPT", "HOSE", "CTCP FPT", "FPT Corporation", "LISTED",
                "Information Technology", "finvera-sector-v1", 1_462_000_000L, price, SessionState.OPEN,
                LocalDate.of(2026, 8, 17), Instant.parse("2026-08-17T07:15:00Z"), DataStatus.PARTIAL,
                List.of("REFERENCE_PRICE_UNAVAILABLE"), "coh-fpt-2");
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
