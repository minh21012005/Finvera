package com.minhnb.finvera_be.analyst;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.analyst.controller.AnalystConversationController;
import com.minhnb.finvera_be.analyst.dto.AnalystConversationDtos.ContextWindowInfo;
import com.minhnb.finvera_be.analyst.dto.AnalystConversationDtos.ConversationPage;
import com.minhnb.finvera_be.analyst.dto.AnalystConversationDtos.ConversationSummary;
import com.minhnb.finvera_be.analyst.service.AnalystConversationExceptions.ConversationConflictException;
import com.minhnb.finvera_be.analyst.service.AnalystConversationExceptions.ConversationNotFoundException;
import com.minhnb.finvera_be.analyst.service.AnalystConversationService;
import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.portfolio.service.OwnerScopedAccess;
import com.minhnb.finvera_be.research.config.InternalApiKeyFilter;
import com.minhnb.finvera_be.shared.api.CorrelationIdFilter;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = AnalystConversationController.class)
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class, InternalApiKeyFilter.class,
        CorrelationIdFilter.class, ProblemDetailsAdvice.class})
class AnalystConversationControllerTests {
    private static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CONVERSATION=UUID.fromString("10000000-0000-0000-0000-000000000001");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", OWNER::toString);
        registry.add("finvera.security.owner.username", () -> "owner-test");
        registry.add("finvera.security.owner.password-hash", () -> "$2a$04$test");
        registry.add("finvera.research.internal-api-key", () -> "test-internal-api-key");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AnalystConversationService service;
    @MockitoBean OwnerScopedAccess owners;

    @Test
    void everyRouteRequiresAuthenticationAndMutationsRequireCsrf() throws Exception {
        mvc.perform(get("/api/v1/analyst/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/analyst/conversations/ask")
                .with(user("owner-test").roles("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestId\":\"20000000-0000-0000-0000-000000000001\",\"question\":\"FPT\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/analyst/conversations/{id}", CONVERSATION)
                .with(user("owner-test").roles("OWNER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Research\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/analyst/conversations/{id}", CONVERSATION)
                .with(user("owner-test").roles("OWNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void validAskReturnsEventStreamAndInvalidPayloadIsRejectedBeforeService() throws Exception {
        when(owners.getAuthenticatedOwnerId()).thenReturn(OWNER);
        when(service.ask(eq(OWNER), any())).thenReturn(new SseEmitter());
        mvc.perform(post("/api/v1/analyst/conversations/ask").with(csrf())
                .with(user("owner-test").roles("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestId\":\"20000000-0000-0000-0000-000000000001\",\"question\":\"FPT\",\"symbol\":\"fpt\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        mvc.perform(post("/api/v1/analyst/conversations/ask").with(csrf())
                .with(user("owner-test").roles("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestId\":\"20000000-0000-0000-0000-000000000001\",\"question\":\" \",\"symbol\":\"bad symbol\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reasonCode").value("INVALID_REQUEST"));
    }

    @Test
    void listValidatesBoundsAndMapsOwnerScopedResult() throws Exception {
        when(owners.getAuthenticatedOwnerId()).thenReturn(OWNER);
        when(service.list(OWNER, null, 20)).thenReturn(new ConversationPage(List.of(summary()), null, false));
        mvc.perform(get("/api/v1/analyst/conversations").with(user("owner-test").roles("OWNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(CONVERSATION.toString()));
        mvc.perform(get("/api/v1/analyst/conversations?limit=0").with(user("owner-test").roles("OWNER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsUnknownAndBusyWithoutDisclosingContent() throws Exception {
        when(owners.getAuthenticatedOwnerId()).thenReturn(OWNER);
        when(service.read(OWNER, CONVERSATION, null, 50)).thenThrow(new ConversationNotFoundException());
        mvc.perform(get("/api/v1/analyst/conversations/{id}/exchanges", CONVERSATION)
                .with(user("owner-test").roles("OWNER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reasonCode").value("CONVERSATION_NOT_FOUND"));

        org.mockito.Mockito.doThrow(new ConversationConflictException("CONVERSATION_BUSY", true))
                .when(service).delete(OWNER, CONVERSATION);
        mvc.perform(delete("/api/v1/analyst/conversations/{id}", CONVERSATION).with(csrf())
                .with(user("owner-test").roles("OWNER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasonCode").value("CONVERSATION_BUSY"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void finalFieldUsesThePublicContractName() throws Exception {
        var exchange=new com.minhnb.finvera_be.analyst.dto.AnalystConversationDtos.ConversationExchange(
                UUID.randomUUID(), 1, "Question", null, "COMPLETED",
                new com.minhnb.finvera_be.analyst.dto.AskAnalystDto.PublicFinalEventDto(
                        "Answer", List.of(), List.of(), false, List.of(), false,
                        "orchestration-v1", "ONLINE", "MODEL",
                        com.minhnb.finvera_be.analyst.dto.AskAnalystDto.ClaimCoverage.FULL),
                null, new ContextWindowInfo("context-window-v1", 0, 0), Instant.now(), Instant.now());
        String json=mapper.writeValueAsString(exchange);
        org.assertj.core.api.Assertions.assertThat(json).contains("\"final\":").doesNotContain("finalResult");
    }

    private static ConversationSummary summary() {
        Instant now=Instant.parse("2026-09-12T01:00:00Z");
        return new ConversationSummary(CONVERSATION, "FPT research", "AUTO", now, now, now, 1, false);
    }
}
