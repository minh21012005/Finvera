package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketRegimeAssessmentInputEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegimeAssessmentInputRepository extends JpaRepository<MarketRegimeAssessmentInputEntity, MarketRegimeAssessmentInputEntity.Key> {
    Optional<MarketRegimeAssessmentInputEntity> findByAssessmentIdAndInputRole(UUID assessmentId, String inputRole);
}
