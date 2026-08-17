package com.minhnb.finvera_be.auth.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.auth.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.OwnerSessionService;
import com.minhnb.finvera_be.auth.application.LoginThrottle;
import com.minhnb.finvera_be.market.api.TcbsRenewalController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import java.time.Instant;

@WebMvcTest(controllers = {OwnerAccessController.class, TcbsRenewalController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class})
class OwnerAccessSecurityTests {

    private static final String PASSWORD = "fixture-owner-password";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    @DynamicPropertySource
    static void ownerProperties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", () -> "00000000-0000-0000-0000-000000000001");
        registry.add("finvera.security.owner.username", () -> "fixture-owner");
        registry.add("finvera.security.owner.password-hash", () -> PASSWORD_HASH);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LoginThrottle throttle;

    @BeforeEach
    void resetThrottle() {
        throttle.reset();
    }

    @Test
    void csrfEndpointIsPublicButSessionStatusIsPrivate() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        mvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/session")
                        .contentType("application/json")
                        .content("""
                                {"username":"fixture-owner","password":"fixture-owner-password"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginRotatesAnExistingSessionAndAbsoluteExpiryDeniesFurtherAccess() throws Exception {
        var preAuthenticationSession = new MockHttpSession();
        String originalId = preAuthenticationSession.getId();
        var login = mvc.perform(post("/api/v1/auth/session")
                        .with(csrf())
                        .session(preAuthenticationSession)
                        .contentType("application/json")
                        .content("""
                                {"username":"fixture-owner","password":"fixture-owner-password"}
                                """))
                .andExpect(status().isNoContent())
                .andReturn();

        var session = (MockHttpSession) login.getRequest().getSession(false);
        org.assertj.core.api.Assertions.assertThat(session.getId()).isNotEqualTo(originalId);
        session.setAttribute(OwnerSessionService.class.getName() + ".expiresAt", Instant.EPOCH);

        mvc.perform(get("/api/v1/auth/session").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validLoginCreatesSecureOwnerSessionAndLogoutInvalidatesIt() throws Exception {
        var login = mvc.perform(post("/api/v1/auth/session")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"fixture-owner","password":"fixture-owner-password"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("FINVERA_SESSION=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andReturn();

        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/v1/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.username").value("fixture-owner"));

        mvc.perform(delete("/api/v1/auth/session").with(csrf()).session(session))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidCredentialsUseAUniformUnauthorizedResponse() throws Exception {
        mvc.perform(post("/api/v1/auth/session")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"not-the-owner","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void repeatedInvalidCredentialsAreRateLimitedWithoutIdentityDisclosure() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/auth/session")
                            .with(csrf())
                            .contentType("application/json")
                            .content("""
                                    {"username":"not-the-owner","password":"wrong"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        }

        mvc.perform(post("/api/v1/auth/session")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"fixture-owner","password":"fixture-owner-password"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    @Test
    void tcbsRenewalIsUnavailableWithoutReadingTheOpaqueValueWhileGateIsOpen() throws Exception {
        var login = mvc.perform(post("/api/v1/auth/session")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"fixture-owner","password":"fixture-owner-password"}
                                """))
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(post("/api/v1/market/providers/tcbs/token-renewal")
                        .with(csrf())
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                {"iOtp":"opaque-sensitive-fixture"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_AUTH_REQUIRED"));
    }
}
