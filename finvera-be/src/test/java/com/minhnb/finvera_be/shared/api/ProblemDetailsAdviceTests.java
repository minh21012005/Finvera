package com.minhnb.finvera_be.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class ProblemDetailsAdviceTests {

    private final ProblemDetailsAdvice advice = new ProblemDetailsAdvice();

    @Test
    void unhandledExceptionBecomesA500ProblemWithoutInternals() {
        // Q-49: previously forwarded to /error and answered 401 AUTHENTICATION_REQUIRED for internal callers.
        var request = new MockHttpServletRequest("GET", "/internal/v1/tools/stocks/VNM/valuation");
        var response = advice.unhandled(request, new IllegalStateException("cannot execute INSERT in a read-only transaction"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getProperties()).containsEntry("code", "SERVER_ERROR");
        assertThat(response.getBody().getTitle()).doesNotContain("INSERT");
        assertThat(response.getBody().getProperties()).containsKey("correlationId");
    }

    @Test
    void responseStatusExceptionKeepsItsStatus() {
        var request = new MockHttpServletRequest("GET", "/internal/v1/tools/market/overview");
        var response = advice.responseStatus(request, new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown owner"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties()).containsEntry("code", "NOT_FOUND");
        assertThat(response.getBody().getTitle()).isEqualTo("Unknown owner");
    }
}
