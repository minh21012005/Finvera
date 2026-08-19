package com.minhnb.finvera_be.stock.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.controller.OwnerAccessController;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.stock.controller.ScreenerController;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T030 [SEC-001, SEC-002]. Mirrors {@code StockDetailSecurityTests}' pattern:
 * this private single-owner deployment (ADR-0005) has no second registered
 * identity to test as "non-owner" — every non-owner request is, by
 * construction, an unauthenticated request, since no registration or
 * multi-identity path exists (SEC-001).
 */
@WebMvcTest(controllers = {OwnerAccessController.class, ScreenerController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class})
class ScreenerSecurityTests {

    private static final String OWNER_NAME = "owner-" + UUID.randomUUID();
    private static final String LOGIN_PROOF = UUID.randomUUID().toString();
    private static final String LOGIN_PROOF_HASH = new BCryptPasswordEncoder(4).encode(LOGIN_PROOF);

    @DynamicPropertySource
    static void ownerProperties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", () -> "00000000-0000-0000-0000-000000000001");
        registry.add("finvera.security.owner.username", () -> OWNER_NAME);
        registry.add("finvera.security.owner.password-hash", () -> LOGIN_PROOF_HASH);
    }

    @Autowired private MockMvc mvc;

    @MockitoBean private ScreenerService screenerService;

    @Test
    void unauthenticatedRequestIsDeniedWithoutInvokingTheScreenerEngine() throws Exception {
        mvc.perform(post("/api/v1/screener/executions").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));

        org.mockito.Mockito.verifyNoInteractions(screenerService);
    }
}
