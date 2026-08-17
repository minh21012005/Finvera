package com.minhnb.finvera_be.auth.filter;

import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class OwnerSessionExpiryFilter extends OncePerRequestFilter {

    private final Clock clock;

    public OwnerSessionExpiryFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var session = request.getSession(false);
        if (session != null) {
            Object value = session.getAttribute(OwnerSessionService.EXPIRES_AT_ATTRIBUTE);
            if (value instanceof Instant expiresAt && !clock.instant().isBefore(expiresAt)) {
                session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
