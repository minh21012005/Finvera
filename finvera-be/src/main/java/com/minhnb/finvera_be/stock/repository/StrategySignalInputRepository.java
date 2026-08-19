package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.StrategySignalInputEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategySignalInputRepository
        extends JpaRepository<StrategySignalInputEntity, StrategySignalInputEntity.Key> {

    List<StrategySignalInputEntity> findBySignalId(UUID signalId);
}
