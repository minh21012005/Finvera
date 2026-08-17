package com.minhnb.finvera_be.auth.controller;

import com.minhnb.finvera_be.auth.dto.CsrfResponse;
import com.minhnb.finvera_be.auth.dto.LoginRequest;
import com.minhnb.finvera_be.auth.dto.OwnerSessionResponse;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class OwnerAccessController {

    private final OwnerSessionService sessions;

    public OwnerAccessController(OwnerSessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/session")
    ResponseEntity<Void> login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        sessions.login(body.username(), body.password(), request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    OwnerSessionResponse current(HttpServletRequest request) {
        var session = sessions.current(request);
        return new OwnerSessionResponse(session.subject(), session.username(),
                session.authenticatedAt(), session.expiresAt());
    }

    @DeleteMapping("/session")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(request, response);
        return ResponseEntity.noContent().build();
    }
}
