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

/**
 * Live TCBS bearer-token lifecycle (contracts/tcbs-iflash-adapter.md "Provider authentication
 * and session lifecycle"). The token is held only in this instance's memory, is never persisted
 * or logged, and is renewed only when the configured owner submits a fresh iOTP through {@link
 * com.minhnb.finvera_be.market.controller.TcbsRenewalController}. TCBS documents a maximum
 * eight-hour token lifetime; the response carries no explicit expiry field, so that ceiling is
 * applied locally.
 *
 * <p>{@code apiKey} never leaves this class: it is read once from server-side configuration and
 * used only as an outbound request field, never echoed back to a caller.
 */
public final class TcbsHttpSessionState implements TcbsSessionState {

    private static final Logger log = LoggerFactory.getLogger(TcbsHttpSessionState.class);
    private static final Duration MAX_TOKEN_LIFETIME = Duration.ofHours(8);
    private static final String REQUEST_OTP_PATH = "/gaia/v1/oauth2/openapi/request-otp";
    private static final String TOKEN_PATH = "/gaia/v1/oauth2/openapi/token";
    private static final TokenState EMPTY = new TokenState(null, Instant.EPOCH, false);

    private final RestClient authClient;
    private final String apiKey;
    private final Clock clock;
    private final AtomicReference<TokenState> state = new AtomicReference<>(EMPTY);

    public TcbsHttpSessionState(String baseUrl, String apiKey, Clock clock) {
        this.apiKey = apiKey;
        this.clock = clock;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.authClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public boolean isTokenPresent() {
        TokenState current = state.get();
        return current.token() != null && current.expiresAt().isAfter(clock.instant());
    }

    @Override
    public boolean isHealthy() {
        return state.get().healthy();
    }

    @Override
    public String requireToken() {
        TokenState current = state.get();
        if (current.token() == null || !current.expiresAt().isAfter(clock.instant())) {
            throw new ProviderAuthenticationRequiredException();
        }
        return current.token();
    }

    /** Called by {@link TcbsHttpRestClient} after a successful market-data call. */
    void markHealthy() {
        state.updateAndGet(TokenState::withHealthy);
    }

    /** Called by {@link TcbsHttpRestClient} after a failed market-data call. */
    void markUnhealthy() {
        state.updateAndGet(TokenState::withUnhealthy);
    }

    /** TCInvest-app TOTP renewal: a single {apiKey, otp} exchange. */
    public void renewWithTotp(String otp) {
        requireApiKeyConfigured();
        requireOtp(otp);
        exchangeToken(new TokenRequestTotp(apiKey, otp));
    }

    /** Email/SMS renewal: request an OTP id first, then exchange {apiKey, otp, otpId}. */
    public void renewWithEmailSms(String otp) {
        requireApiKeyConfigured();
        requireOtp(otp);
        String otpId = requestOtpId();
        exchangeToken(new TokenRequestEmailSms(apiKey, otp, otpId));
    }

    private void requireApiKeyConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("TCBS renewal attempted with no configured owner API key");
            throw new ProviderAuthenticationRequiredException();
        }
    }

    private static void requireOtp(String otp) {
        if (otp == null || otp.isBlank()) {
            throw new IllegalArgumentException("otp is required");
        }
    }

    private String requestOtpId() {
        try {
            OtpIdResponse response = authClient.post()
                    .uri(REQUEST_OTP_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiKeyRequest(apiKey))
                    .retrieve()
                    .body(OtpIdResponse.class);
            String otpId = response == null ? null : response.otpId();
            if (otpId == null || otpId.isBlank()) {
                markUnhealthy();
                throw new ProviderAuthenticationRequiredException();
            }
            return otpId;
        } catch (RestClientException e) {
            log.warn("TCBS request-otp call failed: {}", e.getClass().getSimpleName());
            markUnhealthy();
            throw new ProviderAuthenticationRequiredException();
        }
    }

    private void exchangeToken(Object request) {
        try {
            TokenResponse response = authClient.post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TokenResponse.class);
            String token = response == null ? null : response.token();
            if (token == null || token.isBlank()) {
                markUnhealthy();
                throw new ProviderAuthenticationRequiredException();
            }
            state.set(new TokenState(token, clock.instant().plus(MAX_TOKEN_LIFETIME), true));
            log.info("TCBS live session renewed; token valid for up to {}", MAX_TOKEN_LIFETIME);
        } catch (RestClientException e) {
            log.warn("TCBS token exchange failed: {}", e.getClass().getSimpleName());
            markUnhealthy();
            throw new ProviderAuthenticationRequiredException();
        }
    }

    private record TokenState(String token, Instant expiresAt, boolean healthy) {
        TokenState withHealthy() {
            return new TokenState(token, expiresAt, true);
        }

        TokenState withUnhealthy() {
            return new TokenState(token, expiresAt, false);
        }
    }

    private record ApiKeyRequest(String apiKey) {
    }

    private record OtpIdResponse(String otpId) {
    }

    private record TokenRequestTotp(String apiKey, String otp) {
    }

    private record TokenRequestEmailSms(String apiKey, String otp, String otpId) {
    }

    private record TokenResponse(String token) {
    }
}
