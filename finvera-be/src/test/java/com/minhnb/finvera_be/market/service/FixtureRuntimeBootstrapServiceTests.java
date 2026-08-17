package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.market.repository.EquityPriceObservationRepository;
import com.minhnb.finvera_be.market.repository.MarketBreadthRepository;
import com.minhnb.finvera_be.market.repository.MarketBreadthSnapshotInputRepository;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.market.repository.MarketObservationRepository;
import com.minhnb.finvera_be.market.repository.RegimeAssessmentRepository;
import com.minhnb.finvera_be.market.repository.RegimeAssessmentInputRepository;
import com.minhnb.finvera_be.market.repository.RegimeFactorRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest(properties = "finvera.market.fixture.bootstrap-enabled=true")
@AutoConfigureMockMvc
@Testcontainers
class FixtureRuntimeBootstrapServiceTests {
    private static final String OWNER_NAME = "owner-" + UUID.randomUUID();
    private static final String LOGIN_PROOF = UUID.randomUUID().toString();
    private static final String LOGIN_PROOF_HASH = new BCryptPasswordEncoder(4).encode(LOGIN_PROOF);
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("finvera.security.owner.id", UUID::randomUUID);
        registry.add("finvera.security.owner.username", () -> OWNER_NAME);
        registry.add("finvera.security.owner.password-hash", () -> LOGIN_PROOF_HASH);
    }
    @Autowired FixtureRuntimeBootstrapService bootstrap;
    @Autowired MarketOverviewService overview;
    @Autowired MarketObservationRepository observations;
    @Autowired MarketInstrumentRepository instruments;
    @Autowired EquityPriceObservationRepository equityPrices;
    @Autowired MarketBreadthRepository breadth;
    @Autowired MarketBreadthSnapshotInputRepository breadthInputs;
    @Autowired RegimeAssessmentRepository regimes;
    @Autowired RegimeAssessmentInputRepository regimeInputs;
    @Autowired RegimeFactorRepository regimeFactors;
    @Autowired MockMvc mvc;

    @Test void loadsTraceableP1P2P3DataAndReplayIsIdempotent() {
        var first = overview.latest();
        assertThat(first.indices().indices()).hasSize(4).allMatch(index -> index.level() != null);
        assertThat(first.breadth()).isNotNull();
        assertThat(first.breadth().result().eligible()).isEqualTo(6);
        assertThat(breadthInputs.count()).isEqualTo(6);
        assertThat(first.regime()).isNotNull();
        assertThat(first.regime().assessment().label()).isNotNull();
        assertThat(regimeInputs.count()).isEqualTo(3);
        assertThat(regimeFactors.count()).isEqualTo(5);
        long observationCount = observations.count();
        long breadthCount = breadth.count();
        long regimeCount = regimes.count();

        assertThat(bootstrap.bootstrap().status()).isEqualTo(FixtureRuntimeBootstrapService.Status.ALREADY_APPLIED);
        assertThat(observations.count()).isEqualTo(observationCount);
        assertThat(instruments.count()).isEqualTo(6);
        assertThat(equityPrices.count()).isEqualTo(6);
        assertThat(breadth.count()).isEqualTo(breadthCount);
        assertThat(regimes.count()).isEqualTo(regimeCount);
    }

    @Test void ownerCanReadCompleteBootstrappedOverview() throws Exception {
        var login = mvc.perform(post("/api/v1/auth/session")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(OWNER_NAME, LOGIN_PROOF)))
                .andExpect(status().isNoContent())
                .andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/api/v1/market/overview").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indices.length()").value(4))
                .andExpect(jsonPath("$.breadth.eligible").value(6))
                .andExpect(jsonPath("$.regime.label").isNotEmpty())
                .andExpect(jsonPath("$.regime.factors.length()").value(5));
    }
}
