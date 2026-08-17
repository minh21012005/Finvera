package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketIndexSnapshotEntity;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketIndexSnapshotRepository extends JpaRepository<MarketIndexSnapshotEntity, UUID> {

    Optional<MarketIndexSnapshotEntity> findFirstByIndexIdAndTradingDateOrderByObservedAtDescRevisionDesc(
            UUID indexId, LocalDate tradingDate);

    Optional<MarketIndexSnapshotEntity> findFirstByIndexIdAndTradingDateAndObservedAtOrderByRevisionDesc(
            UUID indexId, LocalDate tradingDate, Instant observedAt);
}
