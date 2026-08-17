package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketRegimeFactorEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegimeFactorRepository extends JpaRepository<MarketRegimeFactorEntity, UUID> {
    List<MarketRegimeFactorEntity> findByAssessmentIdOrderByFactorCode(UUID assessmentId);
}
