package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator;
import com.minhnb.finvera_be.market.domain.breadth.BreadthUniversePolicy;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.entity.MarketBreadthSnapshotEntity;
import com.minhnb.finvera_be.market.entity.MarketBreadthSnapshotInputEntity;
import com.minhnb.finvera_be.market.repository.MarketBreadthRepository;
import com.minhnb.finvera_be.market.repository.MarketBreadthSnapshotInputRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BreadthService {
    private final MarketBreadthRepository snapshots;
    private final MarketBreadthSnapshotInputRepository inputs;
    private final Clock clock;
    public BreadthService(MarketBreadthRepository snapshots, MarketBreadthSnapshotInputRepository inputs, Clock clock) {
        this.snapshots = snapshots; this.inputs = inputs; this.clock = clock;
    }
    @Transactional
    public Snapshot persist(LocalDate tradingDate, Instant asOf, String universeHash,
            BreadthCalculator.Result result, List<InputLink> inputLinks) {
        DataStatus status = statusFor(result);
        UUID id = UUID.randomUUID();
        snapshots.save(new MarketBreadthSnapshotEntity(id, tradingDate, asOf, clock.instant(),
                BreadthUniversePolicy.VERSION, universeHash, result.advancing(), result.declining(), result.unchanged(),
                result.eligible(), result.unclassified(), status.name(), "breadth-v1", result.reasonCodes(), null));
        inputs.saveAll(inputLinks.stream().map(link -> new MarketBreadthSnapshotInputEntity(id, link.instrumentId(),
                link.priceObservationId(), link.classification(), link.reasonCode())).toList());
        return new Snapshot(id, tradingDate, asOf, status, result, BreadthUniversePolicy.VERSION, universeHash);
    }

    /** Persists provider-supplied exchange aggregates without fabricating constituent links. */
    @Transactional
    public Optional<Snapshot> persistProviderAggregate(LocalDate tradingDate, Instant asOf,
            String universeVersion, BreadthCalculator.Result result, String universeHash) {
        if (snapshots.existsByTradingDateAndAsOfAndUniverseRevisionHash(tradingDate, asOf, universeHash)) {
            return Optional.empty();
        }
        UUID id = UUID.randomUUID();
        DataStatus status = statusFor(result);
        snapshots.save(new MarketBreadthSnapshotEntity(id, tradingDate, asOf, clock.instant(),
                universeVersion, universeHash, result.advancing(), result.declining(), result.unchanged(),
                result.eligible(), result.unclassified(), status.name(), "provider-aggregate-v1",
                result.reasonCodes(), null));
        return Optional.of(new Snapshot(id, tradingDate, asOf, status, result, universeVersion, universeHash));
    }
    @Transactional(readOnly = true)
    public Optional<Snapshot> latestFor(LocalDate tradingDate) {
        return snapshots.findFirstByTradingDateOrderByAsOfDescCalculatedAtDesc(tradingDate).map(entity ->
                toSnapshot(entity));
    }

    @Transactional(readOnly = true)
    public Optional<Snapshot> latest() {
        return snapshots.findFirstByOrderByTradingDateDescAsOfDescCalculatedAtDesc().map(BreadthService::toSnapshot);
    }

    private static Snapshot toSnapshot(MarketBreadthSnapshotEntity entity) {
        return new Snapshot(entity.getId(), entity.getTradingDate(), entity.getAsOf(), DataStatus.valueOf(entity.getDataStatus()),
                new BreadthCalculator.Result(entity.getAdvancing(), entity.getDeclining(), entity.getUnchanged(),
                        entity.getUnclassified(), entity.getEligible(), entity.getReasonCodes()), entity.getUniversePolicyVersion(),
                entity.getUniverseRevisionHash());
    }
    private static DataStatus statusFor(BreadthCalculator.Result result) {
        return result.unclassified() > 0
                || result.reasonCodes().contains("REFERENCE_PRICE_UNAVAILABLE_USING_PRIOR_CLOSE")
                ? DataStatus.PARTIAL : DataStatus.CURRENT;
    }
    public record InputLink(UUID instrumentId, UUID priceObservationId, String classification, String reasonCode) { }
    public record Snapshot(UUID id, LocalDate tradingDate, Instant asOf, DataStatus dataStatus, BreadthCalculator.Result result,
                           String universeVersion, String universeHash) { }
}
