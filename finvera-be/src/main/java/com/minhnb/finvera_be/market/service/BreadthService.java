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
        DataStatus status = result.unclassified() > 0 ? DataStatus.PARTIAL : DataStatus.CURRENT;
        UUID id = UUID.randomUUID();
        snapshots.save(new MarketBreadthSnapshotEntity(id, tradingDate, asOf, clock.instant(),
                BreadthUniversePolicy.VERSION, universeHash, result.advancing(), result.declining(), result.unchanged(),
                result.eligible(), result.unclassified(), status.name(), "breadth-v1", result.reasonCodes(), null));
        inputs.saveAll(inputLinks.stream().map(link -> new MarketBreadthSnapshotInputEntity(id, link.instrumentId(),
                link.priceObservationId(), link.classification(), link.reasonCode())).toList());
        return new Snapshot(tradingDate, asOf, status, result, BreadthUniversePolicy.VERSION, universeHash);
    }
    @Transactional(readOnly = true)
    public Optional<Snapshot> latestFor(LocalDate tradingDate) {
        return snapshots.findFirstByTradingDateOrderByAsOfDescCalculatedAtDesc(tradingDate).map(entity ->
                new Snapshot(entity.getTradingDate(), entity.getAsOf(), DataStatus.valueOf(entity.getDataStatus()),
                        new BreadthCalculator.Result(entity.getAdvancing(), entity.getDeclining(), entity.getUnchanged(),
                                entity.getUnclassified(), entity.getEligible(), entity.getReasonCodes()), entity.getUniversePolicyVersion(),
                        entity.getUniverseRevisionHash()));
    }
    public record InputLink(UUID instrumentId, UUID priceObservationId, String classification, String reasonCode) { }
    public record Snapshot(LocalDate tradingDate, Instant asOf, DataStatus dataStatus, BreadthCalculator.Result result,
                           String universeVersion, String universeHash) { }
}
