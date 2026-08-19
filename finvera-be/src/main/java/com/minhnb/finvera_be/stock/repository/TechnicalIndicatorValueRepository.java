package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorValueEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnicalIndicatorValueRepository
        extends JpaRepository<TechnicalIndicatorValueEntity, TechnicalIndicatorValueEntity.Key> {

    List<TechnicalIndicatorValueEntity> findByResultId(UUID resultId);

    /** Feature 003 research R-002: one bulk fetch for every candidate's current indicator results. */
    List<TechnicalIndicatorValueEntity> findByResultIdIn(Collection<UUID> resultIds);
}
