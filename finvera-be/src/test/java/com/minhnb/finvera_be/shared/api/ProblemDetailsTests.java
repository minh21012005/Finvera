package com.minhnb.finvera_be.shared.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ProblemDetailsTests {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new ProblemDetailsAdvice())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void returnsStableSafeProblemWithCallerCorrelationId() throws Exception {
        mvc.perform(get("/failure/auth").header("X-Correlation-ID", "request-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(header().string("X-Correlation-ID", "request-123"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.correlationId").value("request-123"))
                .andExpect(jsonPath("$.detail").value("Authentication failed"));
    }

    @Test
    void replacesUnsafeCorrelationIdAndDoesNotEchoExceptionDetail() throws Exception {
        mvc.perform(get("/failure/provider")
                        .header("X-Correlation-ID", "unsafe value with spaces"))
                .andExpect(status().isBadGateway())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.code").value("PROVIDER_AUTH_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("Live provider authentication is unavailable"));
    }

    @RestController
    static class FailureController {

        @GetMapping("/failure/auth")
        void authenticationFailure() {
            throw new BadCredentialsException("Invalid credentials");
        }

        @GetMapping("/failure/provider")
        void providerFailure() {
            throw new ProblemDetailsAdvice.ProviderAuthRequiredException();
        }
    }
}
