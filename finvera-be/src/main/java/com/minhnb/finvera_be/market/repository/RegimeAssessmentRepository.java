package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketRegimeAssessmentEntity;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegimeAssessmentRepository extends JpaRepository<MarketRegimeAssessmentEntity, UUID> {
    Optional<MarketRegimeAssessmentEntity> findFirstByTradingDateOrderByAsOfDescCalculatedAtDesc(LocalDate tradingDate);
}
