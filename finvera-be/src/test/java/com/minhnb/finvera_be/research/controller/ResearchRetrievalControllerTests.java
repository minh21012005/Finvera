package com.minhnb.finvera_be.research.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.research.config.InternalApiKeyFilter;
import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.research.dto.RetrieveRequest;
import com.minhnb.finvera_be.research.dto.RetrieveResponse;
import com.minhnb.finvera_be.research.service.RetrievalService;
import com.minhnb.finvera_be.shared.api.CorrelationIdFilter;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ResearchRetrievalController.class)
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class, InternalApiKeyFilter.class, CorrelationIdFilter.class, ProblemDetailsAdvice.class})
@EnableConfigurationProperties(ResearchProperties.class)
class ResearchRetrievalControllerTests {

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", () -> "00000000-0000-0000-0000-000000000001");
        registry.add("finvera.security.owner.username", () -> "owner-test");
        registry.add("finvera.security.owner.password-hash", () -> "$2a$04$test");
        registry.add("finvera.research.internal-api-key", () -> "dev-internal-key-change-in-prod");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RetrievalService retrievalService;

    @Test
    void blank_query_returns_400() throws Exception {
        RetrieveRequest request = new RetrieveRequest("   ", null);
        mockMvc.perform(post("/api/v1/research/retrieve")
                        .with(user("owner-test").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overLengthQuery_returns_400() throws Exception {
        String tooLong = "a".repeat(2001);
        RetrieveRequest request = new RetrieveRequest(tooLong, null);
        mockMvc.perform(post("/api/v1/research/retrieve")
                        .with(user("owner-test").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void valid_query_returns_200() throws Exception {
        when(retrievalService.retrievePassages(any(RetrieveRequest.class)))
                .thenReturn(new RetrieveResponse(List.of(), Instant.now()));

        RetrieveRequest request = new RetrieveRequest("Doanh thu FPT?", null);
        mockMvc.perform(post("/api/v1/research/retrieve")
                        .with(user("owner-test").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
