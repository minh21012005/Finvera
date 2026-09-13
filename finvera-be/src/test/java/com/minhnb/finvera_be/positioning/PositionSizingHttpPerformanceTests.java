package com.minhnb.finvera_be.positioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.controller.OwnerAccessController;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.portfolio.dto.PositionsResponse;
import com.minhnb.finvera_be.portfolio.service.PortfolioSizingDataService;
import com.minhnb.finvera_be.portfolio.service.PositionService;
import com.minhnb.finvera_be.positioning.controller.PositionSizingController;
import com.minhnb.finvera_be.positioning.service.PositionSizingMetrics;
import com.minhnb.finvera_be.positioning.service.PositionSizingService;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import com.minhnb.finvera_be.stock.service.StockSizingDataService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = {OwnerAccessController.class, PositionSizingController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class, ProblemDetailsAdvice.class,
        PositionSizingService.class, PositionSizingMetrics.class, PortfolioSizingDataService.class,
        PositionSizingHttpPerformanceTests.Config.class})
class PositionSizingHttpPerformanceTests {
    private static final String OWNER_NAME = "owner-" + UUID.randomUUID();
    private static final String LOGIN_PROOF = UUID.randomUUID().toString();
    private static final String LOGIN_PROOF_HASH = new BCryptPasswordEncoder(4).encode(LOGIN_PROOF);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID PORTFOLIO_ID = UUID.randomUUID();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", () -> "00000000-0000-0000-0000-000000000001");
        registry.add("finvera.security.owner.username", () -> OWNER_NAME);
        registry.add("finvera.security.owner.password-hash", () -> LOGIN_PROOF_HASH);
    }

    @Autowired MockMvc mvc;
    @MockitoBean MarketReferenceDataService market;
    @MockitoBean PositionService positions;
    @MockitoBean StockSizingDataService stocks;
    private MockHttpSession session;

    @BeforeEach void setUp() throws Exception {
        given(market.findActiveInstrumentBySymbol("FPT")).willReturn(java.util.Optional.of(
                new MarketReferenceDataService.InstrumentReference(UUID.randomUUID(), "HOSE", "FPT", "EQUITY", "ACTIVE")));
        given(positions.getPositions(PORTFOLIO_ID)).willReturn(new PositionsResponse(List.of(),
                "30000000", "100000000", "CURRENT", List.of(), "portfolio:http-performance",
                Instant.parse("2026-09-13T07:00:00Z")));
        session = (MockHttpSession) mvc.perform(post("/api/v1/auth/session").with(csrf())
                        .contentType("application/json")
                        .content(JSON.writeValueAsString(Map.of("username", OWNER_NAME, "password", LOGIN_PROOF))))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }

    @Test void authenticatedHttpP95IncludesDecodingSecurityControllerAndPortfolioApplicationRead() throws Exception {
        assertP95(manualJson(), 1_000_000_000L);
        assertP95(portfolioJson(), 2_000_000_000L);
    }

    private void assertP95(String body, long targetNanos) throws Exception {
        for (int i = 0; i < 5; i++) perform(body);
        long[] samples = new long[30];
        for (int i = 0; i < samples.length; i++) {
            long start = System.nanoTime();
            perform(body);
            samples[i] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        assertThat(samples[28]).isLessThan(targetNanos);
    }

    private void perform(String body) throws Exception {
        mvc.perform(post("/api/v1/position-sizing/calculate").with(csrf()).session(session)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
    }

    private static String manualJson() {
        return """
                {"mode":"MANUAL","symbol":"FPT","manualCapital":{"capitalBaseVnd":"100000000","availableCashVnd":"30000000"},
                "riskBudget":{"kind":"PERCENT","value":"0.01"},"priceInput":{"source":"MANUAL","entryPriceVnd":"50000","stopPriceVnd":"47000"},
                "costPolicy":{"excludeCosts":true}}
                """;
    }

    private static String portfolioJson() {
        return """
                {"mode":"PORTFOLIO","symbol":"FPT","portfolioId":"%s",
                "riskBudget":{"kind":"PERCENT","value":"0.01"},"priceInput":{"source":"MANUAL","entryPriceVnd":"50000","stopPriceVnd":"47000"},
                "costPolicy":{"excludeCosts":true}}
                """.formatted(PORTFOLIO_ID);
    }

    @TestConfiguration
    static class Config {
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-09-13T08:00:00Z"), ZoneOffset.UTC); }
        @Bean SimpleMeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }
}
