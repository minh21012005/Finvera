package com.minhnb.finvera_be.market.provider.tcbs;

import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderAuthenticationRequiredException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Owner-renewed, memory-only TCBS JWT state. */
public final class TcbsHttpSessionState {
    private static final Logger log = LoggerFactory.getLogger(TcbsHttpSessionState.class);
    private static final Duration MAX_LIFETIME = Duration.ofHours(8);
    private static final String TOKEN_PATH = "/gaia/v1/oauth2/openapi/token";
    private final RestClient client;
    private final String apiKey;
    private final Clock clock;
    private final AtomicReference<TokenState> token = new AtomicReference<>(new TokenState(null, Instant.EPOCH, false));

    public TcbsHttpSessionState(String baseUrl, String apiKey, Clock clock) {
        this.apiKey = apiKey;
        this.clock = clock;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public void renewWithTotp(String otp) {
        if (apiKey == null || apiKey.isBlank()) throw new ProviderAuthenticationRequiredException();
        if (otp == null || otp.isBlank()) throw new IllegalArgumentException("otp is required");
        try {
            TokenResponse response = client.post().uri(TOKEN_PATH).contentType(MediaType.APPLICATION_JSON)
                    .body(new TokenRequest(apiKey, otp)).retrieve().body(TokenResponse.class);
            if (response == null || response.token() == null || response.token().isBlank()) {
                token.set(new TokenState(null, Instant.EPOCH, false));
                throw new ProviderAuthenticationRequiredException();
            }
            token.set(new TokenState(response.token(), clock.instant().plus(MAX_LIFETIME), true));
            log.info("TCBS live session renewed; token valid for up to {}", MAX_LIFETIME);
        } catch (RestClientException exception) {
            token.set(new TokenState(null, Instant.EPOCH, false));
            log.warn("TCBS token exchange failed: {}", exception.getClass().getSimpleName());
            throw new ProviderAuthenticationRequiredException();
        }
    }

    public String requireToken() {
        TokenState current = token.get();
        if (current.value() == null || !current.expiresAt().isAfter(clock.instant())) {
            throw new ProviderAuthenticationRequiredException();
        }
        return current.value();
    }

    public boolean isTokenPresent() {
        TokenState current = token.get();
        return current.value() != null && current.expiresAt().isAfter(clock.instant());
    }

    public boolean isHealthy() { return token.get().healthy(); }
    public void markHealthy() { token.updateAndGet(value -> new TokenState(value.value(), value.expiresAt(), true)); }
    public void markUnhealthy() { token.updateAndGet(value -> new TokenState(value.value(), value.expiresAt(), false)); }
    public void invalidate() { token.set(new TokenState(null, Instant.EPOCH, false)); }

    private record TokenRequest(String apiKey, String otp) { }
    private record TokenResponse(String token) { }
    private record TokenState(String value, Instant expiresAt, boolean healthy) { }
}
