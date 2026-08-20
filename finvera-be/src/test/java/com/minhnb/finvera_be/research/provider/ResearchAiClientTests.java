package com.minhnb.finvera_be.research.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RetrieveChunksRequest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Verifies the small bounded retry in {@link ResearchAiClient} (Constitution Principle VII: explicit
 * timeouts, bounded retry for safe operations). Uses a bare JDK {@link HttpServer} instead of a mocking
 * library so no new test dependency is needed, and drives it directly rather than through Spring context
 * so retries can be observed at the transport level.
 */
class ResearchAiClientTests {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retriesOnceAfterATransientServerErrorThenSucceeds() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/retrieve", exchange -> {
            if (requestCount.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
            } else {
                byte[] body = "{\"chunks\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            }
        });
        server.start();

        ResearchAiClient client = new ResearchAiClient(clientProperties());
        var response = client.retrieveChunks(new RetrieveChunksRequest(
                "query", UUID.randomUUID(), null, null, null, null, null, 5));

        assertThat(response.chunks()).isEmpty();
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void doesNotRetryOnAClientErrorResponse() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/retrieve", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        });
        server.start();

        ResearchAiClient client = new ResearchAiClient(clientProperties());

        assertThatThrownBy(() -> client.retrieveChunks(new RetrieveChunksRequest(
                        "query", UUID.randomUUID(), null, null, null, null, null, 5)))
                .isInstanceOf(HttpClientErrorException.class);

        assertThat(requestCount.get()).isEqualTo(1);
    }

    private ResearchProperties clientProperties() {
        return new ResearchProperties(
                "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofMinutes(10),
                Duration.ofMinutes(1),
                20 * 1024 * 1024L);
    }
}
