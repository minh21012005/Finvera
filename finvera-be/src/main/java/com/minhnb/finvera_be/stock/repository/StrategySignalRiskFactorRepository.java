package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.StrategySignalRiskFactorEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategySignalRiskFactorRepository
        extends JpaRepository<StrategySignalRiskFactorEntity, StrategySignalRiskFactorEntity.Key> {

    List<StrategySignalRiskFactorEntity> findBySignalId(UUID signalId);

    List<StrategySignalRiskFactorEntity> findBySignalIdIn(Collection<UUID> signalIds);
}
