package com.minhnb.finvera_be.market.provider.tcbs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TcbsThesisWebSocketClientTests {

    @Test
    void authenticatesBeforeSubscribingAndPublishesOnlyValidFrames() {
        Fixture fixture = fixture(Duration.ofDays(1));
        AtomicInteger events = new AtomicInteger();
        fixture.client.observe(ignored -> events.incrementAndGet());
        fixture.client.ensureEquitySubscribed("TCB");
        fixture.client.ensureEquitySubscribed("VNM");

        fixture.client.connect();
        WebSocket.Listener listener = fixture.listener();
        listener.onOpen(fixture.socket);

        verify(fixture.socket).sendText("d|a|||bGl2ZS10b2tlbg==", true);
        listener.onText(fixture.socket, "d|0|{\"success\":true,\"error\":null}", true);
        verify(fixture.socket).sendText("d|s|si|rt|1,2,3,5", true);
        verify(fixture.socket).sendText("d|s|tk|bp+tm|TCB,VNM", true);

        listener.onText(fixture.socket,
                "s|8|{\"indexNumber\":1,\"index\":1728.25,\"change\":12.5}", true);
        listener.onText(fixture.socket, "s|8|{not-json}", true);
        assertThat(events.get()).isEqualTo(1);
        assertThat(fixture.client.health().state().name()).isEqualTo("READY");

        fixture.client.close();
        verify(fixture.socket).sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
    }

    @Test
    void subscribesOnDemandAfterAuthenticationAndSuppressesDuplicates() {
        Fixture fixture = fixture(Duration.ofDays(1));
        fixture.client.connect();
        WebSocket.Listener listener = fixture.listener();
        listener.onOpen(fixture.socket);
        listener.onText(fixture.socket, "d|0|{\"success\":true}", true);

        fixture.client.ensureEquitySubscribed(" tcb ");
        fixture.client.ensureEquitySubscribed("TCB");

        verify(fixture.socket, times(1)).sendText("d|s|tk|bp+tm|TCB", true);
        fixture.client.close();
    }

    @Test
    void evictsLeastRecentlyUsedSymbolWithDocumentedPartialUnsubscribe() {
        Fixture fixture = fixture(Duration.ofDays(1), Duration.ofSeconds(10), 2);
        fixture.client.connect();
        WebSocket.Listener listener = fixture.listener();
        listener.onOpen(fixture.socket);
        listener.onText(fixture.socket, "d|0|{\"success\":true}", true);

        fixture.client.ensureEquitySubscribed("TCB");
        fixture.client.ensureEquitySubscribed("VNM");
        fixture.client.ensureEquitySubscribed("TCB");
        fixture.client.ensureEquitySubscribed("FPT");

        verify(fixture.socket).sendText("d|u|tk|bp+tm|VNM", true);
        verify(fixture.socket, never()).sendText("d|u|tk|bp+tm|TCB", true);
        verify(fixture.socket).sendText("d|s|tk|bp+tm|FPT", true);
        fixture.client.close();
    }

    @Test
    void rejectsInvalidTickerBeforeItCanReachTheSocket() {
        Fixture fixture = fixture(Duration.ofDays(1));

        assertThatThrownBy(() -> fixture.client.ensureEquitySubscribed("TCB,VNM"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(fixture.socket, never()).sendText("d|s|tk|bp+tm|TCB,VNM", true);
        fixture.client.close();
    }

    @Test
    void restoresRetainedSymbolsAfterReconnectAuthentication() {
        Fixture fixture = fixture(Duration.ofDays(1));
        fixture.client.ensureEquitySubscribed("TCB");
        fixture.client.connect();
        WebSocket.Listener first = fixture.listener(1);
        first.onOpen(fixture.socket);
        first.onText(fixture.socket, "d|0|{\"success\":true}", true);
        first.onClose(fixture.socket, WebSocket.NORMAL_CLOSURE, "provider-close");

        fixture.client.connect();
        WebSocket.Listener second = fixture.listener(2);
        second.onOpen(fixture.socket);
        second.onText(fixture.socket, "d|0|{\"success\":true}", true);

        verify(fixture.socket, times(2)).sendText("d|s|tk|bp+tm|TCB", true);
        fixture.client.close();
    }

    @Test
    void rejectedAuthenticationInvalidatesSessionAndStopsTheSocket() {
        Fixture fixture = fixture(Duration.ofDays(1));
        fixture.client.connect();
        WebSocket.Listener listener = fixture.listener();
        listener.onOpen(fixture.socket);

        listener.onText(fixture.socket, "d|0|{\"success\":false,\"error\":\"denied\"}", true);

        verify(fixture.session).invalidate();
        verify(fixture.socket).abort();
        fixture.client.close();
    }

    @Test
    void sendsDocumentedTextHeartbeatAfterAuthentication() {
        Fixture fixture = fixture(Duration.ofMillis(10));
        fixture.client.connect();
        WebSocket.Listener listener = fixture.listener();
        listener.onOpen(fixture.socket);
        listener.onText(fixture.socket, "d|0|{\"success\":true}", true);

        verify(fixture.socket, timeout(500).atLeastOnce()).sendText("d|p|||", true);
        fixture.client.close();
    }

    @Test
    void disconnectMarksTheProviderUnhealthyAndCanBeClosedSafely() {
        Fixture fixture = fixture(Duration.ofDays(1));
        fixture.client.connect();
        WebSocket.Listener listener = fixture.listener();
        listener.onOpen(fixture.socket);

        listener.onClose(fixture.socket, WebSocket.NORMAL_CLOSURE, "provider-close");

        verify(fixture.session).markUnhealthy();
        fixture.client.close();
    }

    @Test
    void abortsAndMarksUnhealthyWhenAuthenticationDoesNotComplete() {
        Fixture fixture = fixture(Duration.ofDays(1), Duration.ofMillis(10));
        fixture.client.connect();
        fixture.listener().onOpen(fixture.socket);

        verify(fixture.socket, timeout(500)).abort();
        verify(fixture.session, timeout(500)).markUnhealthy();
        fixture.client.close();
    }

    private Fixture fixture(Duration heartbeat) {
        return fixture(heartbeat, Duration.ofSeconds(10));
    }

    private Fixture fixture(Duration heartbeat, Duration authenticationTimeout) {
        return fixture(heartbeat, authenticationTimeout, 100);
    }

    private Fixture fixture(Duration heartbeat, Duration authenticationTimeout, int maxDynamicSymbols) {
        TcbsHttpSessionState session = mock(TcbsHttpSessionState.class);
        when(session.requireToken()).thenReturn("live-token");
        when(session.isTokenPresent()).thenReturn(true);
        when(session.isHealthy()).thenReturn(true);

        HttpClient httpClient = mock(HttpClient.class);
        WebSocket.Builder builder = mock(WebSocket.Builder.class);
        WebSocket socket = mock(WebSocket.class);
        when(httpClient.newWebSocketBuilder()).thenReturn(builder);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(socket.sendText(any(), eq(true))).thenReturn(CompletableFuture.completedFuture(socket));
        when(socket.sendClose(any(Integer.class), any())).thenReturn(CompletableFuture.completedFuture(socket));
        when(builder.buildAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(socket));

        TcbsThesisWebSocketClient client = new TcbsThesisWebSocketClient(
                URI.create("wss://example.invalid/ws"), session, heartbeat, Duration.ofSeconds(30),
                maxDynamicSymbols,
                Clock.fixed(Instant.parse("2026-08-24T03:00:01Z"), ZoneOffset.UTC), httpClient,
                authenticationTimeout);
        return new Fixture(client, session, builder, socket);
    }

    private record Fixture(TcbsThesisWebSocketClient client, TcbsHttpSessionState session,
            WebSocket.Builder builder, WebSocket socket) {
        WebSocket.Listener listener() {
            return listener(1);
        }
        WebSocket.Listener listener(int invocation) {
            ArgumentCaptor<WebSocket.Listener> listener = ArgumentCaptor.forClass(WebSocket.Listener.class);
            verify(builder, times(invocation)).buildAsync(any(), listener.capture());
            return listener.getAllValues().get(invocation - 1);
        }
    }
}
