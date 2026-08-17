package com.minhnb.finvera_be.shared.api;

import com.minhnb.finvera_be.auth.service.LoginThrottle.LoginRateLimitedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemDetailsAdvice {

    public static final String CORRELATION_ATTRIBUTE = ProblemDetailsAdvice.class.getName() + ".correlationId";

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> invalidCredentials(HttpServletRequest request) {
        return response(request, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed");
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    ResponseEntity<ProblemDetail> loginRateLimited(HttpServletRequest request) {
        return response(request, HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED", "Try again later");
    }

    @ExceptionHandler(ProviderAuthRequiredException.class)
    ResponseEntity<ProblemDetail> providerAuthRequired(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_GATEWAY, "PROVIDER_AUTH_REQUIRED",
                "Live provider authentication is unavailable");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidRequest(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed");
    }

    public static void write(
            HttpServletResponse response,
            HttpServletRequest request,
            int status,
            String code,
            String title) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        String correlationId = correlationId(request);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title
                + "\",\"status\":" + status + ",\"code\":\"" + code
                + "\",\"correlationId\":\"" + correlationId + "\"}");
    }

    private static ResponseEntity<ProblemDetail> response(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String title) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId(request));
        return ResponseEntity.status(status).body(problem);
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CORRELATION_ATTRIBUTE);
        return value instanceof String correlation ? correlation : UUID.randomUUID().toString();
    }

    public static final class ProviderAuthRequiredException extends RuntimeException {
        public ProviderAuthRequiredException() {
            super("Provider authentication is required");
        }
    }
}
