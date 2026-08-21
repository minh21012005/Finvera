package com.minhnb.finvera_be.market.provider.tcbs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderAuthenticationRequiredException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link TcbsHttpRestClient} against a bare JDK {@link HttpServer} standing in for TCBS
 * (same house pattern as {@code ResearchAiClientTests}). Two response shapes are exercised for
 * every numeric field — JSON string and JSON number — because the confirmed POC evidence never
 * pinned down which one TCBS actually returns (Constitution: never guess provider schema).
 */
class TcbsHttpRestClientTests {

    private static final LocalDate TRADING_DATE = LocalDate.of(2026, 1, 15);

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchTickerCommonsParsesNumericFieldsSentAsJsonStrings() throws Exception {
        startServerWithTickerCommonsBody("""
                {"tradingDate":"2026-01-15","data":[
                  {"symbol":"VNINDEX","indexNumber":1,"matchPrice":"1300.000000","refPrice":"1280.000000",
                   "change":"20.000000","changePercent":"1.5589","totalVol":"500000000","totalVal":"9000000000000",
                   "open":"1290","high":"1310","low":"1270"}
                ]}
                """);
        TcbsHttpSessionState session = renewedSession();
        TcbsHttpRestClient client = new TcbsHttpRestClient(baseUrl(), session);

        var response = client.fetchTickerCommons(TRADING_DATE);

        assertThat(response.tradingDate()).isEqualTo("2026-01-15");
        assertThat(response.data()).hasSize(1);
        var item = response.data().get(0);
        assertThat(item.symbol()).isEqualTo("VNINDEX");
        assertThat(item.indexNumber()).isEqualTo(1);
        assertThat(item.matchPrice()).isEqualTo("1300.000000");
        assertThat(item.refPrice()).isEqualTo("1280.000000");
    }

    @Test
    void fetchTickerCommonsParsesNumericFieldsSentAsJsonNumbers() throws Exception {
        startServerWithTickerCommonsBody("""
                {"tradingDate":"2026-01-15","data":[
                  {"symbol":"VNINDEX","indexNumber":1,"matchPrice":1300.0,"refPrice":1280.0,
                   "change":20.0,"changePercent":1.5589,"totalVol":500000000,"totalVal":9000000000000}
                ]}
                """);
        TcbsHttpSessionState session = renewedSession();
        TcbsHttpRestClient client = new TcbsHttpRestClient(baseUrl(), session);

        var response = client.fetchTickerCommons(TRADING_DATE);

        var item = response.data().get(0);
        assertThat(item.matchPrice()).isEqualTo("1300.0");
        assertThat(item.refPrice()).isEqualTo("1280.0");
        assertThat(item.totalVol()).isEqualTo("500000000");
    }

    @Test
    void fetchTickerCommonsRequestsAllFourAllowlistedIndicesInOneCall() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> capturedQuery = new java.util.concurrent.atomic.AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tartarus/v1/tickerCommons", exchange -> {
            requestCount.incrementAndGet();
            capturedQuery.set(exchange.getRequestURI().getQuery());
            byte[] body = "{\"tradingDate\":\"2026-01-15\",\"data\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        TcbsHttpRestClient client = new TcbsHttpRestClient(baseUrl(), renewedSession());
        client.fetchTickerCommons(TRADING_DATE);

        assertThat(requestCount.get()).isEqualTo(1);
        String decodedQuery = java.net.URLDecoder.decode(capturedQuery.get(), StandardCharsets.UTF_8);
        assertThat(decodedQuery).contains("index=1,2,3,5");
    }

    @Test
    void a401ResponseThrowsAuthenticationRequiredAndDoesNotRetry() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tartarus/v1/tickerCommons", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();

        TcbsHttpRestClient client = new TcbsHttpRestClient(baseUrl(), renewedSession());

        assertThatThrownBy(() -> client.fetchTickerCommons(TRADING_DATE))
                .isInstanceOf(ProviderAuthenticationRequiredException.class);
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void aTransientServerErrorIsRetriedOnceThenSucceeds() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tartarus/v1/tickerCommons", exchange -> {
            if (requestCount.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
            } else {
                byte[] body = "{\"tradingDate\":\"2026-01-15\",\"data\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            }
        });
        server.start();

        TcbsHttpRestClient client = new TcbsHttpRestClient(baseUrl(), renewedSession());
        var response = client.fetchTickerCommons(TRADING_DATE);

        assertThat(response.data()).isEmpty();
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void fetchSecuritiesParsesTheContentArrayEnvelope() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ananke/v1/securities", exchange -> {
            byte[] body = """
                    {"content":[{"symbol":"TCB","tradePlace":"HOSE"}],"totalElements":1}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        TcbsHttpRestClient client = new TcbsHttpRestClient(baseUrl(), renewedSession());
        var securities = client.fetchSecurities(TRADING_DATE);

        assertThat(securities).hasSize(1);
        assertThat(securities.get(0).symbol()).isEqualTo("TCB");
    }

    private void startServerWithTickerCommonsBody(String json) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tartarus/v1/tickerCommons", exchange -> {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** A session with a token already set, bypassing the auth-endpoint round trip for these tests. */
    private TcbsHttpSessionState renewedSession() {
        TcbsHttpSessionState session = new TcbsHttpSessionState(baseUrl(), "unused", fixedClock());
        // Route the token exchange through the same fake server: the "token" context is never
        // registered by these tests, so seed the token via a tiny dedicated auth exchange instead
        // of duplicating TcbsHttpSessionState's internals here.
        server.createContext("/gaia/v1/oauth2/openapi/token", exchange -> {
            byte[] body = "{\"token\":\"sanitized-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        session.renewWithTotp("123456");
        return session;
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-01-15T02:00:00Z"), ZoneOffset.UTC);
    }
}
