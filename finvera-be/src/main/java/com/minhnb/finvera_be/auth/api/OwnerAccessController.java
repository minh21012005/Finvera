package com.minhnb.finvera_be.auth.api;

import com.minhnb.finvera_be.auth.OwnerSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    OwnerSessionService.SessionSummary current(HttpServletRequest request) {
        return sessions.current(request);
    }

    @DeleteMapping("/session")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    record CsrfResponse(String token, String headerName) {
    }

    record LoginRequest(
            @NotBlank @Size(max = 128) String username,
            @NotBlank @Size(max = 256) String password) {

        @Override
        public String toString() {
            return "LoginRequest[username=<redacted>, password=<redacted>]";
        }
    }
}
