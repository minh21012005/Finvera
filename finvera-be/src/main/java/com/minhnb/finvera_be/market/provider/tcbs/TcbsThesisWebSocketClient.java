package com.minhnb.finvera_be.market.provider.tcbs;

import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealth;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealthState;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/** Bounded, read-only client for the TCBS Thesis cash price-board stream. */
public final class TcbsThesisWebSocketClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TcbsThesisWebSocketClient.class);
    private static final String INDEX_SUBSCRIPTION = "d|s|si|rt|1,2,3,5";
    private static final Duration AUTHENTICATION_TIMEOUT = Duration.ofSeconds(10);
    private final URI uri;
    private final TcbsHttpSessionState session;
    private final Duration heartbeatInterval;
    private final Duration reconnectMaxDelay;
    private final Duration authenticationTimeoutDuration;
    private final int maxDynamicSymbols;
    private final Object subscriptionsLock = new Object();
    private final LinkedHashMap<String, Boolean> dynamicSymbols = new LinkedHashMap<>(16, 0.75f, true);
    private final Clock clock;
    private final HttpClient httpClient;
    private final TcbsThesisFrameMapper mapper = new TcbsThesisFrameMapper();
    private final JsonMapper json = JsonMapper.builder().build();
    private final CopyOnWriteArrayList<Consumer<TcbsThesisFrameMapper.Event>> observers = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tcbs-thesis-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean authenticated = new AtomicBoolean();
    private final AtomicInteger reconnectAttempt = new AtomicInteger();
    private volatile ScheduledFuture<?> heartbeat;
    private volatile ScheduledFuture<?> authenticationTimeout;

    public TcbsThesisWebSocketClient(URI uri, TcbsHttpSessionState session, Duration heartbeatInterval,
            Duration reconnectMaxDelay, int maxDynamicSymbols, Clock clock) {
        this(uri, session, heartbeatInterval, reconnectMaxDelay, maxDynamicSymbols, clock,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), AUTHENTICATION_TIMEOUT);
    }

    TcbsThesisWebSocketClient(URI uri, TcbsHttpSessionState session, Duration heartbeatInterval,
            Duration reconnectMaxDelay, int maxDynamicSymbols, Clock clock, HttpClient httpClient,
            Duration authenticationTimeoutDuration) {
        this.uri = Objects.requireNonNull(uri);
        this.session = Objects.requireNonNull(session);
        this.heartbeatInterval = Objects.requireNonNull(heartbeatInterval);
        this.reconnectMaxDelay = Objects.requireNonNull(reconnectMaxDelay);
        this.authenticationTimeoutDuration = Objects.requireNonNull(authenticationTimeoutDuration);
        if (maxDynamicSymbols <= 0) throw new IllegalArgumentException("maxDynamicSymbols must be positive");
        this.maxDynamicSymbols = maxDynamicSymbols;
        this.clock = Objects.requireNonNull(clock);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    public AutoCloseable observe(Consumer<TcbsThesisFrameMapper.Event> observer) {
        observers.add(Objects.requireNonNull(observer));
        return () -> observers.remove(observer);
    }

    /** Retains a validated equity symbol and subscribes it immediately when authenticated. */
    public void ensureEquitySubscribed(String requestedSymbol) {
        String symbol = normalizeSymbol(requestedSymbol);
        synchronized (subscriptionsLock) {
            if (dynamicSymbols.containsKey(symbol)) {
                dynamicSymbols.get(symbol);
                return;
            }
            String evicted = null;
            if (dynamicSymbols.size() >= maxDynamicSymbols) {
                var iterator = dynamicSymbols.entrySet().iterator();
                evicted = iterator.next().getKey();
                iterator.remove();
            }
            dynamicSymbols.put(symbol, Boolean.TRUE);
            WebSocket active = socket.get();
            if (active != null && authenticated.get()) {
                if (evicted != null) active.sendText("d|u|tk|bp+tm|" + evicted, true);
                active.sendText("d|s|tk|bp+tm|" + symbol, true);
            }
        }
    }

    public void connect() {
        if (closed.get() || socket.get() != null || !connecting.compareAndSet(false, true)) return;
        final String token;
        try {
            token = session.requireToken();
        } catch (RuntimeException exception) {
            connecting.set(false);
            return;
        }
        httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, new Listener(token))
                .whenComplete((webSocket, error) -> {
                    connecting.set(false);
                    if (error != null) {
                        session.markUnhealthy();
                        log.warn("TCBS Thesis connection failed: {}", error.getClass().getSimpleName());
                        scheduleReconnect();
                    }
                });
    }

    public ProviderHealth health() {
        if (!session.isTokenPresent()) return new ProviderHealth(ProviderHealthState.AUTH_REQUIRED, "PROVIDER_AUTH_REQUIRED");
        if (socket.get() == null || !session.isHealthy()) return new ProviderHealth(ProviderHealthState.DEGRADED, "PROVIDER_CONNECTIVITY_FAILED");
        return new ProviderHealth(ProviderHealthState.READY, "READY");
    }

    private void authenticated(WebSocket webSocket) {
        if (socket.get() != webSocket) return;
        cancelAuthenticationTimeout();
        reconnectAttempt.set(0);
        session.markHealthy();
        int retainedSymbols;
        synchronized (subscriptionsLock) {
            authenticated.set(true);
            webSocket.sendText(INDEX_SUBSCRIPTION, true);
            retainedSymbols = dynamicSymbols.size();
            if (!dynamicSymbols.isEmpty()) {
                webSocket.sendText("d|s|tk|bp+tm|" + String.join(",", dynamicSymbols.keySet()), true);
            }
        }
        ScheduledFuture<?> current = heartbeat;
        if (current != null) current.cancel(false);
        heartbeat = scheduler.scheduleAtFixedRate(() -> {
            WebSocket active = socket.get();
            if (active != null) active.sendText("d|p|||", true);
        }, heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
        log.info("TCBS Thesis stream authenticated and subscribed; retained_dynamic_symbols={}", retainedSymbols);
    }

    private boolean isAuthSuccess(String raw) {
        if (!raw.startsWith("d|0|")) return false;
        try {
            return json.readTree(raw.substring(4)).path("success").booleanValue();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void publish(String raw) {
        try {
            var event = mapper.map(raw, clock.instant());
            observers.forEach(observer -> {
                try {
                    observer.accept(event);
                } catch (RuntimeException exception) {
                    log.warn("TCBS Thesis observer failed: {}", exception.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException exception) {
            log.warn("TCBS Thesis frame rejected: {}", exception.getClass().getSimpleName());
        }
    }

    private void disconnected(WebSocket webSocket) {
        if (!socket.compareAndSet(webSocket, null)) return;
        authenticated.set(false);
        cancelAuthenticationTimeout();
        session.markUnhealthy();
        ScheduledFuture<?> current = heartbeat;
        if (current != null) current.cancel(false);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (closed.get() || !session.isTokenPresent()) return;
        int attempt = Math.min(reconnectAttempt.incrementAndGet(), 10);
        long delaySeconds = Math.min(1L << Math.min(attempt, 5), reconnectMaxDelay.toSeconds());
        scheduler.schedule(this::connect, Math.max(1, delaySeconds), TimeUnit.SECONDS);
    }

    private void cancelAuthenticationTimeout() {
        ScheduledFuture<?> current = authenticationTimeout;
        if (current != null) current.cancel(false);
        authenticationTimeout = null;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ScheduledFuture<?> current = heartbeat;
        if (current != null) current.cancel(false);
        cancelAuthenticationTimeout();
        authenticated.set(false);
        WebSocket active = socket.getAndSet(null);
        if (active != null) active.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        scheduler.shutdownNow();
    }

    private final class Listener implements WebSocket.Listener {
        private final String encodedToken;
        private final StringBuilder fragments = new StringBuilder();
        private Listener(String token) {
            encodedToken = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        }
        @Override public void onOpen(WebSocket webSocket) {
            if (closed.get()) {
                webSocket.abort();
                return;
            }
            authenticated.set(false);
            socket.set(webSocket);
            webSocket.request(1);
            webSocket.sendText("d|a|||" + encodedToken, true);
            authenticationTimeout = scheduler.schedule(() -> {
                if (socket.compareAndSet(webSocket, null)) {
                    authenticated.set(false);
                    session.markUnhealthy();
                    webSocket.abort();
                    log.warn("TCBS Thesis authentication timed out");
                    scheduleReconnect();
                }
            }, authenticationTimeoutDuration.toMillis(), TimeUnit.MILLISECONDS);
        }
        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) {
                String raw = fragments.toString();
                fragments.setLength(0);
                if (isAuthSuccess(raw)) authenticated(webSocket);
                else if (raw.startsWith("d|0|")) {
                    cancelAuthenticationTimeout();
                    authenticated.set(false);
                    log.warn("TCBS Thesis authentication rejected");
                    socket.compareAndSet(webSocket, null);
                    session.invalidate();
                    webSocket.abort();
                } else publish(raw);
            }
            webSocket.request(1);
            return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            disconnected(webSocket);
            return null;
        }
        @Override public void onError(WebSocket webSocket, Throwable error) {
            log.warn("TCBS Thesis stream disconnected: {}", error.getClass().getSimpleName());
            disconnected(webSocket);
        }
    }

    private static String normalizeSymbol(String requestedSymbol) {
        if (requestedSymbol == null) throw new IllegalArgumentException("symbol is required");
        String symbol = requestedSymbol.trim().toUpperCase(Locale.ROOT);
        if (!symbol.matches("[A-Z0-9]{1,10}")) {
            throw new IllegalArgumentException("invalid TCBS ticker");
        }
        return symbol;
    }
}
