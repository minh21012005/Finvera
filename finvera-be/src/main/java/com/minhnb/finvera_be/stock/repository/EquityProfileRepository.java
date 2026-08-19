package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquityProfileRepository extends JpaRepository<EquityProfileEntity, UUID> {

    Optional<EquityProfileEntity> findFirstByInstrumentIdAndEffectiveToIsNull(UUID instrumentId);

    /** Feature 003 research R-007: the screener's candidate universe root. */
    List<EquityProfileEntity> findByEffectiveToIsNullAndListingStatus(String listingStatus);
}
