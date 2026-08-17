package com.minhnb.finvera_be.shared.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Correlation-ID";
    private static final int MAX_LENGTH = 128;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String correlationId = valid(supplied) ? supplied : UUID.randomUUID().toString();
        request.setAttribute(ProblemDetailsAdvice.CORRELATION_ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        try (var ignored = MDC.putCloseable("correlationId", correlationId)) {
            filterChain.doFilter(request, response);
        }
    }

    private static boolean valid(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= MAX_LENGTH
                && value.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '-' || ch == '_');
    }
}
