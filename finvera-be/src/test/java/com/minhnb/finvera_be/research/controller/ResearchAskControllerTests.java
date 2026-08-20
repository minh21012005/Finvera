package com.minhnb.finvera_be.research.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.research.config.InternalApiKeyFilter;
import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.research.dto.AskRequest;
import com.minhnb.finvera_be.research.service.AskService;
import com.minhnb.finvera_be.shared.api.CorrelationIdFilter;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(controllers = ResearchAskController.class)
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class, InternalApiKeyFilter.class, CorrelationIdFilter.class, ProblemDetailsAdvice.class})
@EnableConfigurationProperties(ResearchProperties.class)
class ResearchAskControllerTests {

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
    private AskService askService;

    @Test
    void unauthenticated_request_is_rejected_401() throws Exception {
        AskRequest request = new AskRequest("Doanh thu FPT?", null);
        mockMvc.perform(post("/api/v1/research/ask")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrf_token_required_for_ask() throws Exception {
        AskRequest request = new AskRequest("Doanh thu FPT?", null);
        mockMvc.perform(post("/api/v1/research/ask")
                        .with(user("owner-test").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void blank_query_returns_400() throws Exception {
        AskRequest request = new AskRequest("   ", null);
        mockMvc.perform(post("/api/v1/research/ask")
                        .with(user("owner-test").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void valid_ask_returns_200_event_stream() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(askService.askQuestion(any(AskRequest.class))).thenReturn(emitter);

        AskRequest request = new AskRequest("Doanh thu FPT?", null);
        mockMvc.perform(post("/api/v1/research/ask")
                        .with(user("owner-test").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }
}
