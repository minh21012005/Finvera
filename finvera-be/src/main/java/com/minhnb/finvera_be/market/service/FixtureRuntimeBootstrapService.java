package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator;
import com.minhnb.finvera_be.market.domain.breadth.BreadthUniversePolicy;
import com.minhnb.finvera_be.market.domain.breadth.BreadthUniversePolicy.InstrumentType;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.AdjustmentStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV1;
import com.minhnb.finvera_be.market.entity.EquityPriceObservationEntity;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.entity.MarketObservationEntity;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderObservation;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderSnapshotBatch;
import com.minhnb.finvera_be.market.repository.EquityPriceObservationRepository;
import com.minhnb.finvera_be.market.repository.MarketBreadthRepository;
import com.minhnb.finvera_be.market.repository.MarketIndexRepository;
import com.minhnb.finvera_be.market.repository.MarketIndexSnapshotRepository;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.market.repository.MarketObservationRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Loads one deterministic, traceable P1-P3 dataset for local acceptance only. */
@Service
@ConditionalOnProperty(name = "finvera.market.fixture.bootstrap-enabled", havingValue = "true")
@ConditionalOnProperty(name = "finvera.market.provider.mode", havingValue = "fixture")
public class FixtureRuntimeBootstrapService {
    static final String RESOURCE = "fixtures/market/runtime-complete.json";
    private final String providerMode;
    private final MarketIngestionService ingestion;
    private final MarketObservationRepository observations;
    private final MarketInstrumentRepository instruments;
    private final EquityPriceObservationRepository equityPrices;
    private final MarketIndexRepository indices;
    private final MarketIndexSnapshotRepository indexSnapshots;
    private final MarketBreadthRepository breadthRepository;
    private final BreadthService breadthService;
    private final RegimeAssessmentService regimeService;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    public FixtureRuntimeBootstrapService(@Value("${finvera.market.provider.mode}") String providerMode,
            MarketIngestionService ingestion, MarketObservationRepository observations,
            MarketInstrumentRepository instruments, EquityPriceObservationRepository equityPrices,
            MarketIndexRepository indices, MarketIndexSnapshotRepository indexSnapshots,
            MarketBreadthRepository breadthRepository, BreadthService breadthService,
            RegimeAssessmentService regimeService, Clock clock) {
        this.providerMode=providerMode; this.ingestion=ingestion; this.observations=observations;
        this.instruments=instruments; this.equityPrices=equityPrices; this.indices=indices;
        this.indexSnapshots=indexSnapshots; this.breadthRepository=breadthRepository;
        this.breadthService=breadthService; this.regimeService=regimeService; this.clock=clock;
    }

    @Transactional
    public Result bootstrap() {
        if (!"fixture".equals(providerMode)) throw new IllegalStateException("Fixture bootstrap requires fixture provider mode");
        JsonNode root = readPackage();
        JsonNode payload = root.path("payload");
        String packageHash = required(root, "packageSha256");
        if (!packageHash.equals(sha256(payload.toString()))) throw new IllegalStateException("Fixture package checksum mismatch");
        if (!"market-fixture-runtime-v1".equals(required(root, "contractVersion"))
                || !"FINVERA_FIXTURE".equals(required(payload, "source"))) {
            throw new IllegalStateException("Fixture package contract/source is not allowlisted");
        }
        LocalDate date = LocalDate.parse(required(payload, "tradingDate"));
        Instant asOf = Instant.parse(required(payload, "asOf"));
        String universeHash = sha256(payload.path("instruments").toString());
        if (breadthRepository.existsByTradingDateAndAsOfAndUniverseRevisionHash(date, asOf, universeHash)) {
            return new Result(Status.ALREADY_APPLIED, packageHash);
        }

        ingestIndices(payload, date, asOf);
        List<BreadthCalculator.SecurityInput> securityInputs = new ArrayList<>();
        List<BreadthService.InputLink> breadthLinks = new ArrayList<>();
        for (JsonNode item : payload.path("instruments")) {
            UUID instrumentId = UUID.fromString(required(item, "id"));
            String symbol = required(item, "symbol");
            Venue venue = Venue.valueOf(required(item, "venue"));
            BigDecimal price = decimal(item, "price");
            BigDecimal reference = decimal(item, "reference");
            instruments.save(new MarketInstrumentEntity(instrumentId, required(item, "isin"), venue.name(), symbol,
                    "COMMON_EQUITY", date.minusYears(1), null, "ACTIVE", "FINVERA_FIXTURE", packageHash));
            UUID ingestionId = deterministicId("ingestion:" + symbol);
            UUID priceId = deterministicId("price:" + symbol);
            observations.save(new MarketObservationEntity(ingestionId, "FINVERA_FIXTURE", "EQUITY_PRICE", symbol,
                    date, asOf, asOf, clock.instant(), null, sha256(item.toString()), "ACCEPTED", null, null));
            equityPrices.save(new EquityPriceObservationEntity(priceId, instrumentId, ingestionId, date, asOf,
                    price, reference, price, null, "RAW", null));
            securityInputs.add(new BreadthCalculator.SecurityInput(venue, symbol, required(item, "isin"), true,
                    false, InstrumentType.COMMON_EQUITY, price, reference, AdjustmentStatus.RAW));
            String classification = price.compareTo(reference) > 0 ? "ADVANCING"
                    : price.compareTo(reference) < 0 ? "DECLINING" : "UNCHANGED";
            breadthLinks.add(new BreadthService.InputLink(instrumentId, priceId, classification, null));
        }
        var breadthResult = new BreadthCalculator(new BreadthUniversePolicy()).calculate(securityInputs);
        var breadth = breadthService.persist(date, asOf, universeHash, breadthResult, breadthLinks);
        var vnIndex = indices.findByCode("VN_INDEX").orElseThrow();
        UUID vnIndexSnapshot = indexSnapshots.findFirstByIndexIdAndTradingDateOrderByObservedAtDescRevisionDesc(
                vnIndex.getId(), date).orElseThrow().getId();
        var engine = new MarketRegimeV1();
        List<MarketRegimeV1.ComponentScore> scores = new ArrayList<>();
        JsonNode scoreNode = payload.path("regimeScores");
        for (MarketRegimeV1.Component component : MarketRegimeV1.Component.values()) {
            scores.add(new MarketRegimeV1.ComponentScore(component, decimal(scoreNode, component.name())));
        }
        var assessment = engine.assess(scores,
                new MarketRegimeV1.InputAvailability(true, true, DataStatus.CURRENT, DataStatus.CURRENT));
        regimeService.persist(new RegimeAssessmentService.AssessmentCommand(date, asOf, assessment, null,
                List.of(RegimeAssessmentService.InputLink.indexSnapshot("VN_INDEX_CURRENT", vnIndexSnapshot),
                        RegimeAssessmentService.InputLink.breadthSnapshot("BREADTH_CURRENT", breadth.id()),
                        RegimeAssessmentService.InputLink.inputSet("VN_INDEX_HISTORY", packageHash)),
                List.of(new RegimeAssessmentService.SourceValue("FINVERA_FIXTURE", "VN_INDEX_CURRENT",
                        decimal(payload.path("indices").get(0), "level")))));
        return new Result(Status.APPLIED, packageHash);
    }

    private void ingestIndices(JsonNode payload, LocalDate date, Instant asOf) {
        List<ProviderObservation> values = new ArrayList<>();
        for (JsonNode item : payload.path("indices")) {
            values.add(new ProviderObservation(IndexCode.valueOf(required(item, "code")), decimal(item, "level"),
                    decimal(item, "referenceLevel"), null, null, Long.valueOf(required(item, "matchedVolume")),
                    decimal(item, "matchedValueVnd"), List.of()));
        }
        ingestion.ingest(new ProviderSnapshotBatch("FINVERA_FIXTURE", date, asOf,
                SessionState.valueOf(required(payload, "sessionState")), DataStatus.CURRENT, List.of(), values));
    }

    private JsonNode readPackage() {
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) { return json.readTree(input); }
        catch (IOException exception) { throw new IllegalStateException("Cannot read fixture package", exception); }
    }
    private static String required(JsonNode node, String field) {
        JsonNode value=node.path(field); if(!value.isTextual() || value.stringValue().isBlank())
            throw new IllegalStateException("Missing fixture field: " + field); return value.stringValue();
    }
    private static BigDecimal decimal(JsonNode node, String field) { return new BigDecimal(required(node, field)); }
    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static UUID deterministicId(String value) { return UUID.nameUUIDFromBytes(("finvera-fixture:" + value).getBytes(StandardCharsets.UTF_8)); }
    public enum Status { APPLIED, ALREADY_APPLIED }
    public record Result(Status status, String packageHash) { }
}
