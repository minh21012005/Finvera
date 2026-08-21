package com.minhnb.finvera_be.market.provider.tcbs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderAuthenticationRequiredException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the TCBS token lifecycle without ever hitting the real provider: a bare JDK
 * {@link HttpServer} stands in for {@code https://openapi.tcbs.com.vn}, mirroring the pattern
 * already used for {@code ResearchAiClientTests}.
 */
class TcbsHttpSessionStateTests {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void isTokenPresentIsFalseBeforeAnyRenewal() {
        TcbsHttpSessionState state = new TcbsHttpSessionState("http://127.0.0.1:1", "key", fixedClock());

        assertThat(state.isTokenPresent()).isFalse();
        assertThatThrownBy(state::requireToken).isInstanceOf(ProviderAuthenticationRequiredException.class);
    }

    @Test
    void renewWithTotpExchangesApiKeyAndOtpForAToken() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gaia/v1/oauth2/openapi/token", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"token\":\"sanitized-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        TcbsHttpSessionState state = new TcbsHttpSessionState(baseUrl(), "test-api-key", fixedClock());
        state.renewWithTotp("123456");

        assertThat(state.isTokenPresent()).isTrue();
        assertThat(state.requireToken()).isEqualTo("sanitized-token");
        assertThat(capturedBody.get()).contains("test-api-key").contains("123456").doesNotContain("otpId");
    }

    @Test
    void renewWithEmailSmsRequestsAnOtpIdBeforeExchangingTheToken() throws Exception {
        AtomicInteger requestOtpCalls = new AtomicInteger();
        AtomicReference<String> capturedTokenBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gaia/v1/oauth2/openapi/request-otp", exchange -> {
            requestOtpCalls.incrementAndGet();
            byte[] body = "{\"otpId\":\"otp-id-123\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/gaia/v1/oauth2/openapi/token", exchange -> {
            capturedTokenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"token\":\"sanitized-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        TcbsHttpSessionState state = new TcbsHttpSessionState(baseUrl(), "test-api-key", fixedClock());
        state.renewWithEmailSms("654321");

        assertThat(requestOtpCalls.get()).isEqualTo(1);
        assertThat(capturedTokenBody.get()).contains("otp-id-123").contains("654321");
        assertThat(state.isTokenPresent()).isTrue();
    }

    @Test
    void renewWithoutAConfiguredApiKeyThrowsAuthenticationRequired() {
        TcbsHttpSessionState state = new TcbsHttpSessionState("http://127.0.0.1:1", "", fixedClock());

        assertThatThrownBy(() -> state.renewWithTotp("123456"))
                .isInstanceOf(ProviderAuthenticationRequiredException.class);
    }

    @Test
    void renewWithBlankOtpIsRejectedBeforeAnyNetworkCall() {
        TcbsHttpSessionState state = new TcbsHttpSessionState("http://127.0.0.1:1", "key", fixedClock());

        assertThatThrownBy(() -> state.renewWithTotp(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRejectedTokenExchangeLeavesTheSessionUnhealthyAndTokenAbsent() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gaia/v1/oauth2/openapi/token", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();

        TcbsHttpSessionState state = new TcbsHttpSessionState(baseUrl(), "test-api-key", fixedClock());

        assertThatThrownBy(() -> state.renewWithTotp("000000"))
                .isInstanceOf(ProviderAuthenticationRequiredException.class);
        assertThat(state.isTokenPresent()).isFalse();
        assertThat(state.isHealthy()).isFalse();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-01-15T02:00:00Z"), ZoneOffset.UTC);
    }
}
