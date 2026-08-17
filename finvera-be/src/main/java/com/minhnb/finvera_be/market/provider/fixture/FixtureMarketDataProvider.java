package com.minhnb.finvera_be.market.provider.fixture;

import static com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealthState.AUTH_REQUIRED;
import static com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealthState.DEGRADED;
import static com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealthState.READY;

import com.minhnb.finvera_be.market.provider.MarketDataProvider;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Deterministic development/test provider. Fixture names are compile-time allowlisted. */
public final class FixtureMarketDataProvider implements MarketDataProvider {

    public static final String SOURCE = "FINVERA_FIXTURE";
    private static final String FIXTURE_ROOT = "fixtures/market/";
    private static final List<IndexCode> SUPPORTED_INDICES = List.of(
            IndexCode.VN_INDEX, IndexCode.VN30, IndexCode.HNX_INDEX, IndexCode.UPCOM_INDEX);

    private final FixtureScenario scenario;
    private final JsonMapper jsonMapper;

    public FixtureMarketDataProvider(FixtureScenario scenario) {
        this(scenario, JsonMapper.builder().build());
    }

    FixtureMarketDataProvider(FixtureScenario scenario, JsonMapper jsonMapper) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    @Override
    public ProviderReferenceBatch fetchReferenceData(LocalDate effectiveDate) {
        requireAvailable();
        return new ProviderReferenceBatch(SOURCE, effectiveDate, SUPPORTED_INDICES);
    }

    @Override
    public ProviderSnapshotBatch reconcileLatest(LocalDate tradingDate) {
        requireAvailable();
        ProviderSnapshotBatch batch = readFixture(scenario.resourceName());
        if (!batch.tradingDate().equals(tradingDate)) {
            throw new IllegalArgumentException("No fixture exists for requested trading date");
        }
        return batch;
    }

    @Override
    public Subscription subscribe(Consumer<ProviderObservation> observer) {
        Objects.requireNonNull(observer, "observer");
        requireAvailable();
        ProviderSnapshotBatch batch = readFixture(scenario.resourceName());
        batch.observations().forEach(observer);
        return () -> { };
    }

    @Override
    public ProviderHealth health() {
        if (scenario == FixtureScenario.AUTH_REQUIRED) {
            return new ProviderHealth(AUTH_REQUIRED, "PROVIDER_AUTH_REQUIRED");
        }
        if (scenario != FixtureScenario.COMPLETE) {
            return new ProviderHealth(DEGRADED, "FIXTURE_" + scenario.name());
        }
        return new ProviderHealth(READY, "READY");
    }

    private void requireAvailable() {
        if (scenario == FixtureScenario.AUTH_REQUIRED) {
            throw new ProviderAuthenticationRequiredException();
        }
    }

    private ProviderSnapshotBatch readFixture(String resourceName) {
        try (InputStream input = new ClassPathResource(FIXTURE_ROOT + resourceName).getInputStream()) {
            JsonNode root = jsonMapper.readTree(input);
            String source = requiredText(root, "source");
            if (!SOURCE.equals(source)) {
                throw new IllegalStateException("Fixture source is not allowlisted");
            }
            List<ProviderObservation> observations = stream(root.path("indices"))
                    .map(this::mapObservation)
                    .toList();
            if (!observations.stream().map(ProviderObservation::code).toList().equals(SUPPORTED_INDICES)) {
                throw new IllegalStateException("Fixture index set/order is not allowlisted");
            }
            return new ProviderSnapshotBatch(
                    source,
                    LocalDate.parse(requiredText(root, "tradingDate")),
                    Instant.parse(requiredText(root, "observedAt")),
                    SessionState.valueOf(requiredText(root, "sessionState")),
                    DataStatus.valueOf(requiredText(root, "dataStatus")),
                    textList(root.path("reasonCodes")),
                    observations);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read normalized fixture", exception);
        }
    }

    private ProviderObservation mapObservation(JsonNode node) {
        return new ProviderObservation(
                IndexCode.valueOf(requiredText(node, "code")),
                decimalOrNull(node, "level"),
                decimalOrNull(node, "referenceLevel"),
                decimalOrNull(node, "absoluteChange"),
                decimalOrNull(node, "percentageChange"),
                longOrNull(node, "matchedVolume"),
                decimalOrNull(node, "matchedValueVnd"),
                textList(node.path("reasonCodes")));
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : new BigDecimal(value.stringValue());
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : Long.valueOf(value.stringValue());
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.stringValue().isBlank()) {
            throw new IllegalStateException("Missing normalized fixture field: " + field);
        }
        return value.stringValue();
    }

    private static List<String> textList(JsonNode node) {
        return stream(node).map(JsonNode::stringValue).toList();
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
        if (!array.isArray()) {
            return java.util.stream.Stream.empty();
        }
        return java.util.stream.StreamSupport.stream(array.spliterator(), false);
    }

    public enum FixtureScenario {
        COMPLETE("index-complete.json"),
        DELAYED("index-delayed.json"),
        STALE("index-stale.json"),
        MISSING("index-missing.json"),
        CORRECTION("index-correction.json"),
        AUTH_REQUIRED(null);

        private final String resourceName;

        FixtureScenario(String resourceName) {
            this.resourceName = resourceName;
        }

        String resourceName() {
            return resourceName;
        }
    }
}
