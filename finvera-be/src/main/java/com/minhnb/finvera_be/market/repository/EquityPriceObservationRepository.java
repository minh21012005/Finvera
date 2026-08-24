package com.minhnb.finvera_be.market.repository;
import com.minhnb.finvera_be.market.entity.EquityPriceObservationEntity;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EquityPriceObservationRepository extends JpaRepository<EquityPriceObservationEntity, UUID> {
    Optional<EquityPriceObservationEntity> findFirstByInstrumentIdOrderByObservedAtDesc(UUID instrumentId);
}
