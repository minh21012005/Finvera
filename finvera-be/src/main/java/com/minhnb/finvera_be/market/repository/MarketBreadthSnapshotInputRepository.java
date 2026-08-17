package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketBreadthSnapshotInputEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketBreadthSnapshotInputRepository
        extends JpaRepository<MarketBreadthSnapshotInputEntity, MarketBreadthSnapshotInputEntity.Key> { }
