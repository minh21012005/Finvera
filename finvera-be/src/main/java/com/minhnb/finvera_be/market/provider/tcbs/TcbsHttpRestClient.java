package com.minhnb.finvera_be.market.provider.tcbs;

import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderAuthenticationRequiredException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Live HTTP implementation of {@link TcbsRestClient} against the endpoints approved in
 * {@code contracts/tcbs-iflash-adapter.md}: {@code GET /tartarus/v1/tickerCommons} (index
 * subjects via {@code index=} and, per the official TCBS OpenAPI docs, individual equity
 * subjects via the sibling {@code tickers=} parameter on the same endpoint) and {@code GET
 * /ananke/v1/securities}. No other path is ever called.
 *
 * <p>Response fields are read as a generic JSON tree rather than typed POJOs so a field returned
 * as either a JSON string or a JSON number is handled identically without guessing which shape
 * TCBS uses (Constitution: "No external market-data schema or provider behavior may be
 * guessed").
 */
public final class TcbsHttpRestClient implements TcbsRestClient {

    private static final Logger log = LoggerFactory.getLogger(TcbsHttpRestClient.class);
    private static final String TICKER_COMMONS_PATH = "/tartarus/v1/tickerCommons";
    private static final String SECURITIES_PATH = "/ananke/v1/securities";
    private static final String ALLOWLISTED_INDEX_NUMBERS = "1,2,3,5";
    private static final int MAX_ATTEMPTS = 2;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(300);

    private final RestClient restClient;
    private final TcbsHttpSessionState sessionState;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public TcbsHttpRestClient(String baseUrl, TcbsHttpSessionState sessionState) {
        this.sessionState = sessionState;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public TickerCommonsResponse fetchTickerCommons(LocalDate tradingDate) {
        String rawBody = withAuthAndRetry("fetchTickerCommons", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path(TICKER_COMMONS_PATH)
                        .queryParam("index", ALLOWLISTED_INDEX_NUMBERS)
                        .build())
                .header("Authorization", "Bearer " + sessionState.requireToken())
                .retrieve()
                .body(String.class));
        return parseTickerCommons(rawBody, tradingDate);
    }

    @Override
    public List<SecurityRecord> fetchSecurities(LocalDate effectiveDate) {
        String rawBody = withAuthAndRetry("fetchSecurities", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path(SECURITIES_PATH).queryParam("fields", "all").build())
                .header("Authorization", "Bearer " + sessionState.requireToken())
                .retrieve()
                .body(String.class));
        return parseSecurities(rawBody);
    }

    private <T> T withAuthAndRetry(String operation, Supplier<T> call) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                T result = call.get();
                sessionState.markHealthy();
                return result;
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                sessionState.markUnhealthy();
                throw new ProviderAuthenticationRequiredException();
            } catch (HttpServerErrorException | ResourceAccessException e) {
                lastFailure = e;
                sessionState.markUnhealthy();
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("Transient failure calling TCBS ({}), attempt {}/{}: {}",
                            operation, attempt, MAX_ATTEMPTS, e.getClass().getSimpleName());
                    sleepBeforeRetry();
                }
            }
        }
        throw lastFailure;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying TCBS call", ie);
        }
    }

    private TickerCommonsResponse parseTickerCommons(String rawBody, LocalDate tradingDate) {
        JsonNode root = jsonMapper.readTree(rawBody);
        String responseTradingDate = textOrDefault(root, "tradingDate", tradingDate.toString());
        List<TickerCommonsItem> items = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            items.add(new TickerCommonsItem(
                    textOrDefault(item, "symbol", null),
                    numberOrDefault(item, "indexNumber", 0),
                    decimalStringOrNull(item, "matchPrice"),
                    decimalStringOrNull(item, "change"),
                    decimalStringOrNull(item, "changePercent"),
                    decimalStringOrNull(item, "totalVol"),
                    decimalStringOrNull(item, "totalVal"),
                    decimalStringOrNull(item, "open"),
                    decimalStringOrNull(item, "high"),
                    decimalStringOrNull(item, "low"),
                    decimalStringOrNull(item, "refPrice")));
        }
        return new TickerCommonsResponse(responseTradingDate, items);
    }

    private List<SecurityRecord> parseSecurities(String rawBody) {
        JsonNode root = jsonMapper.readTree(rawBody);
        // The paginated envelope nests securities under "content"; tolerate a bare array too.
        JsonNode content = root.has("content") ? root.path("content") : root;
        List<SecurityRecord> records = new ArrayList<>();
        for (JsonNode item : content) {
            String symbol = textOrDefault(item, "symbol", null);
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            records.add(new SecurityRecord(symbol, textOrDefault(item, "tradePlace", null)));
        }
        return records;
    }

    /** Handles a field TCBS may return as either a JSON string or a JSON number without guessing which. */
    private static String decimalStringOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue().toPlainString();
        }
        String text = value.stringValue();
        return text == null || text.isBlank() ? null : text;
    }

    private static String textOrDefault(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        if (value.isTextual()) {
            return value.stringValue();
        }
        return value.isNumber() ? value.decimalValue().toPlainString() : fallback;
    }

    private static int numberOrDefault(JsonNode node, String field, int fallback) {
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return value.intValue();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.stringValue());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }
}
