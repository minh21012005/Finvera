package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquityProfileRepository extends JpaRepository<EquityProfileEntity, UUID> {

    Optional<EquityProfileEntity> findFirstByInstrumentIdAndEffectiveToIsNull(UUID instrumentId);
}
