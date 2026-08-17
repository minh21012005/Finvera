package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketRegimeAssessmentInputEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegimeAssessmentInputRepository extends JpaRepository<MarketRegimeAssessmentInputEntity, MarketRegimeAssessmentInputEntity.Key> {
}
