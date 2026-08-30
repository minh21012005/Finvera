package com.minhnb.finvera_be.research.config;

import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that protects internal endpoints under {@code /internal/v1/**} hosted by {@code finvera-be}
 * (such as the ingestion callback), enforcing the shared {@code X-Internal-Api-Key} header (SEC-001, research R-003, F1).
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final ResearchProperties researchProperties;

    public InternalApiKeyFilter(
            @org.springframework.beans.factory.annotation.Autowired(required = false) ResearchProperties researchProperties) {
        // Absent properties mean no secret is configured: every /internal/v1 request is refused.
        this.researchProperties = researchProperties;
    }

    /** Length-independent comparison so a wrong key cannot be timed byte by byte. */
    private static boolean constantTimeEquals(String presented, String expected) {
        byte[] a = presented.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/internal/v1");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String key = request.getHeader(INTERNAL_API_KEY_HEADER);
        if (researchProperties == null || key == null || !constantTimeEquals(key, researchProperties.internalApiKey())) {
            ProblemDetailsAdvice.write(response, request, 401, "UNAUTHORIZED", "Invalid or missing X-Internal-Api-Key");
            return;
        }

        // Authenticate request as internal service
        var auth = new UsernamePasswordAuthenticationToken(
                "internal-service",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"), new SimpleGrantedAuthority("ROLE_OWNER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
