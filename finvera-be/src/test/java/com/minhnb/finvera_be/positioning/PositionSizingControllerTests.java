package com.minhnb.finvera_be.positioning;

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
import com.minhnb.finvera_be.positioning.controller.PositionSizingController;
import com.minhnb.finvera_be.positioning.dto.PositionSizingDtos.SizingRequest;
import com.minhnb.finvera_be.positioning.dto.PositionSizingDtos.SizingResult;
import com.minhnb.finvera_be.positioning.service.PositionSizingExceptions.InvalidSizingRequestException;
import com.minhnb.finvera_be.positioning.service.PositionSizingService;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import java.time.Instant;
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

@WebMvcTest(controllers = {OwnerAccessController.class, PositionSizingController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class, ProblemDetailsAdvice.class})
class PositionSizingControllerTests {
    private static final String OWNER_NAME = "owner-" + UUID.randomUUID();
    private static final String LOGIN_PROOF = UUID.randomUUID().toString();
    private static final String LOGIN_PROOF_HASH = new BCryptPasswordEncoder(4).encode(LOGIN_PROOF);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("finvera.security.owner.id", () -> "00000000-0000-0000-0000-000000000001");
        r.add("finvera.security.owner.username", () -> OWNER_NAME);
        r.add("finvera.security.owner.password-hash", () -> LOGIN_PROOF_HASH);
    }
    @Autowired MockMvc mvc;
    @MockitoBean PositionSizingService service;

    @Test void requiresOwnerAndCsrf() throws Exception {
        mvc.perform(post("/api/v1/position-sizing/calculate").contentType("application/json").content(validJson()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/position-sizing/calculate").session(ownerSession())
                        .contentType("application/json").content(validJson()))
                .andExpect(status().isForbidden());
    }

    @Test void returnsContractResponse() throws Exception {
        given(service.calculate(any(SizingRequest.class))).willReturn(sample());
        mvc.perform(post("/api/v1/position-sizing/calculate").with(csrf()).session(ownerSession())
                        .contentType("application/json").content(validJson()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CALCULATED"))
                .andExpect(jsonPath("$.quantity").value(1000)).andExpect(jsonPath("$.sizingRuleVersion").value("position-sizing-v1"));
    }

    @Test void mapsBoundedValidationReasonTo422() throws Exception {
        given(service.calculate(any(SizingRequest.class))).willThrow(new InvalidSizingRequestException("INCOMPLETE_COST_POLICY"));
        mvc.perform(post("/api/v1/position-sizing/calculate").with(csrf()).session(ownerSession())
                        .contentType("application/json").content(validJson()))
                .andExpect(status().isUnprocessableEntity()).andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.reasonCode").value("INCOMPLETE_COST_POLICY"));
    }

    @Test void rejectsUnknownRequestFieldsAtTheTrustBoundary() throws Exception {
        mvc.perform(post("/api/v1/position-sizing/calculate").with(csrf()).session(ownerSession())
                        .contentType("application/json").content(validJson().replace("\"symbol\":\"FPT\"", "\"symbol\":\"FPT\",\"invented\":1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reasonCode").value("INVALID_REQUEST"));
    }

    @Test void mapsBeanValidationFailuresToTheContractual422() throws Exception {
        mvc.perform(post("/api/v1/position-sizing/calculate").with(csrf()).session(ownerSession())
                        .contentType("application/json").content(validJson().replace("\"FPT\"", "\"fpt\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reasonCode").value("INVALID_REQUEST"));
    }

    private MockHttpSession ownerSession() throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/session").with(csrf()).contentType("application/json")
                .content(JSON.writeValueAsString(Map.of("username", OWNER_NAME, "password", LOGIN_PROOF))))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    private static String validJson() { return """
        {"mode":"MANUAL","symbol":"FPT","manualCapital":{"capitalBaseVnd":"1000000","availableCashVnd":"1000000"},
        "riskBudget":{"kind":"FIXED_VND","value":"100000"},"priceInput":{"source":"MANUAL","entryPriceVnd":"1000","stopPriceVnd":"900"},
        "costPolicy":{"excludeCosts":true}}
        """; }
    private static SizingResult sample() { return new SizingResult("CALCULATED", "FPT", "MANUAL", 1000L, 1000L,
            100, 0L, "1000000", "1000000", "1000", "900", "1000", "900", true, "100000", "1000", "900", "100",
            "1000000", "100000", "0", null, null, null, List.of(), List.of(), List.of(),
            List.of("COSTS_EXCLUDED"), "position-sizing-v1", "market-lot-v1", Instant.parse("2026-09-12T08:00:00Z")); }
}
