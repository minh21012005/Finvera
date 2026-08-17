package com.minhnb.finvera_be.auth.service;

import com.minhnb.finvera_be.auth.config.OwnerProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class OwnerSessionService {

    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(8);
    private static final String AUTHENTICATED_AT = OwnerSessionService.class.getName() + ".authenticatedAt";
    public static final String EXPIRES_AT_ATTRIBUTE = OwnerSessionService.class.getName() + ".expiresAt";

    private final OwnerProperties owner;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository contextRepository;
    private final LoginThrottle throttle;
    private final Clock clock;

    public OwnerSessionService(
            OwnerProperties owner,
            PasswordEncoder passwordEncoder,
            SecurityContextRepository contextRepository,
            LoginThrottle throttle,
            Clock clock) {
        this.owner = owner;
        this.passwordEncoder = passwordEncoder;
        this.contextRepository = contextRepository;
        this.throttle = throttle;
        this.clock = clock;
    }

    public SessionSummary login(
            String username,
            String password,
            HttpServletRequest request,
            HttpServletResponse response) {
        throttle.checkAllowed();

        boolean passwordMatches = passwordEncoder.matches(password, owner.passwordHash());
        boolean usernameMatches = owner.username().equals(username);
        if (!usernameMatches || !passwordMatches) {
            throttle.recordFailure();
            throw new BadCredentialsException("Invalid owner credentials");
        }

        throttle.reset();
        var previousSession = request.getSession(false);
        if (previousSession != null) {
            request.changeSessionId();
        }
        var session = request.getSession(true);
        session.setMaxInactiveInterval((int) IDLE_TIMEOUT.toSeconds());

        Instant authenticatedAt = clock.instant();
        Instant expiresAt = authenticatedAt.plus(ABSOLUTE_TIMEOUT);
        session.setAttribute(AUTHENTICATED_AT, authenticatedAt);
        session.setAttribute(EXPIRES_AT_ATTRIBUTE, expiresAt);

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                owner.username(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(session.getId(), ABSOLUTE_TIMEOUT).toString());
        return new SessionSummary(owner.id(), owner.username(), authenticatedAt, expiresAt);
    }

    public SessionSummary current(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            throw new BadCredentialsException("Owner session is unavailable");
        }
        var authenticatedAt = (Instant) session.getAttribute(AUTHENTICATED_AT);
        var expiresAt = (Instant) session.getAttribute(EXPIRES_AT_ATTRIBUTE);
        if (authenticatedAt == null || expiresAt == null || !clock.instant().isBefore(expiresAt)) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            throw new BadCredentialsException("Owner session is unavailable");
        }
        return new SessionSummary(owner.id(), owner.username(), authenticatedAt, expiresAt);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
    }

    private static ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from("FINVERA_SESSION", value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public record SessionSummary(
            java.util.UUID subject,
            String username,
            Instant authenticatedAt,
            Instant expiresAt) {
    }
}
