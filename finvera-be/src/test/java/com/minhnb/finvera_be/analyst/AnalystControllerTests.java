package com.minhnb.finvera_be.analyst;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.minhnb.finvera_be.analyst.config.AnalystProperties;
import com.minhnb.finvera_be.analyst.controller.AnalystController;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.AskAnalystRequest;
import com.minhnb.finvera_be.analyst.service.AnalystService;
import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.portfolio.service.OwnerScopedAccess;
import com.minhnb.finvera_be.research.config.InternalApiKeyFilter;
import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.shared.api.CorrelationIdFilter;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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

@WebMvcTest(controllers = AnalystController.class)
@Import({
        OwnerSecurityConfiguration.class,
        OwnerSessionService.class,
        InternalApiKeyFilter.class,
        CorrelationIdFilter.class,
        ProblemDetailsAdvice.class
})
@EnableConfigurationProperties({ResearchProperties.class, AnalystProperties.class})
class AnalystControllerTests {

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", () -> "00000000-0000-0000-0000-000000000001");
        registry.add("finvera.security.owner.username", () -> "owner-test");
        registry.add("finvera.security.owner.password-hash", () -> "$2a$04$test");
        registry.add("finvera.research.internal-api-key", () -> "dev-internal-key-change-in-prod");
        registry.add("finvera.analyst.internal-api-key", () -> "dev-internal-key-change-in-prod");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AnalystService analystService;

    @MockitoBean
    private OwnerScopedAccess ownerScopedAccess;

    @Test
    void ask_unauthenticated_returnsUnauthorized() throws Exception {
        AskAnalystRequest request = new AskAnalystRequest("Giá HPG hôm nay", "HPG", Collections.emptyList());
        mockMvc.perform(post("/api/v1/analyst/ask")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ask_withoutCsrf_returnsForbidden() throws Exception {
        AskAnalystRequest request = new AskAnalystRequest("Giá HPG hôm nay", "HPG", Collections.emptyList());
        mockMvc.perform(post("/api/v1/analyst/ask")
                        .with(user("owner-test").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ask_validRequest_returnsEventStream() throws Exception {
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(ownerScopedAccess.getAuthenticatedOwnerId()).thenReturn(ownerId);
        when(analystService.askStream(any(), any())).thenReturn(new SseEmitter());

        AskAnalystRequest request = new AskAnalystRequest("Giá HPG hôm nay", "HPG", Collections.emptyList());
        mockMvc.perform(post("/api/v1/analyst/ask")
                        .with(csrf())
                        .with(user("owner-test").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void explain_unauthenticated_returnsUnauthorized() throws Exception {
        var req = new com.minhnb.finvera_be.analyst.dto.AskAnalystDto.ExplainRequest(
                "SIGNAL",
                "HPG",
                List.of(new com.minhnb.finvera_be.analyst.dto.AskAnalystDto.EvidenceFactorDto("RSI", "RSI 70")));

        mockMvc.perform(post("/api/v1/analyst/explanations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void explain_validRequest_returns200AndExplanation() throws Exception {
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(ownerScopedAccess.getAuthenticatedOwnerId()).thenReturn(ownerId);

        var expectedRes = new com.minhnb.finvera_be.analyst.dto.AskAnalystDto.ExplainResponse(
                "Tín hiệu mua do RSI 70",
                true);
        when(analystService.explainOutput(any(), any())).thenReturn(expectedRes);

        var req = new com.minhnb.finvera_be.analyst.dto.AskAnalystDto.ExplainRequest(
                "SIGNAL",
                "HPG",
                List.of(new com.minhnb.finvera_be.analyst.dto.AskAnalystDto.EvidenceFactorDto("RSI", "RSI 70")));

        mockMvc.perform(post("/api/v1/analyst/explanations")
                        .with(csrf())
                        .with(user("owner-test").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
