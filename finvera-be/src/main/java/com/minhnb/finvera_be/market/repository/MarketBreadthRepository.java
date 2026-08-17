package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketBreadthSnapshotEntity;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketBreadthRepository extends JpaRepository<MarketBreadthSnapshotEntity, UUID> {
    Optional<MarketBreadthSnapshotEntity> findFirstByTradingDateOrderByAsOfDescCalculatedAtDesc(LocalDate tradingDate);
    boolean existsByTradingDateAndAsOfAndUniverseRevisionHash(LocalDate tradingDate, Instant asOf, String universeRevisionHash);
}
