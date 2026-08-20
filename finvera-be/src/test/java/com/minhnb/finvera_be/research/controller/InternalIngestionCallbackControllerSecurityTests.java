package com.minhnb.finvera_be.research.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.research.config.InternalApiKeyFilter;
import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.research.service.IngestionCallbackService;
import com.minhnb.finvera_be.shared.api.CorrelationIdFilter;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
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

@WebMvcTest(controllers = {InternalIngestionCallbackController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class, InternalApiKeyFilter.class, CorrelationIdFilter.class, ProblemDetailsAdvice.class})
@EnableConfigurationProperties(ResearchProperties.class)
class InternalIngestionCallbackControllerSecurityTests {

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", () -> "00000000-0000-0000-0000-000000000001");
        registry.add("finvera.security.owner.username", () -> "owner-test");
        registry.add("finvera.security.owner.password-hash", () -> "$2a$04$test");
        registry.add("finvera.research.internal-api-key", () -> "dev-internal-key-change-in-prod");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionCallbackService callbackService;

    @Test
    void rejectsCallbackWithoutInternalApiKey() throws Exception {
        UUID itemId = UUID.randomUUID();
        mockMvc.perform(patch("/internal/v1/ingestions/{id}/callback", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\",\"chunks\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reasonCode").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsCallbackWithInvalidInternalApiKey() throws Exception {
        UUID itemId = UUID.randomUUID();
        mockMvc.perform(patch("/internal/v1/ingestions/{id}/callback", itemId)
                        .header("X-Internal-Api-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\",\"chunks\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reasonCode").value("UNAUTHORIZED"));
    }

    @Test
    void acceptsCallbackWithValidInternalApiKey() throws Exception {
        UUID itemId = UUID.randomUUID();
        doNothing().when(callbackService).handleCallback(eq(itemId), any());

        mockMvc.perform(patch("/internal/v1/ingestions/{id}/callback", itemId)
                        .header("X-Internal-Api-Key", "dev-internal-key-change-in-prod")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\",\"chunks\":[]}"))
                .andExpect(status().isNoContent());
    }
}
