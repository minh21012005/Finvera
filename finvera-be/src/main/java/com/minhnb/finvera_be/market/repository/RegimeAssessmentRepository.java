package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketRegimeAssessmentEntity;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface RegimeAssessmentRepository extends JpaRepository<MarketRegimeAssessmentEntity, UUID> {
    Optional<MarketRegimeAssessmentEntity> findFirstByTradingDateOrderByAsOfDescCalculatedAtDesc(LocalDate tradingDate);

    Optional<MarketRegimeAssessmentEntity> findFirstByTradingDateAndAssessmentBasisOrderByAsOfDescCalculatedAtDesc(
            LocalDate tradingDate, String assessmentBasis);

    Optional<MarketRegimeAssessmentEntity> findFirstByOrderByTradingDateDescAsOfDescCalculatedAtDesc();

    Optional<MarketRegimeAssessmentEntity> findFirstByAssessmentBasisOrderByTradingDateDescAsOfDescCalculatedAtDesc(
            String assessmentBasis);

    List<MarketRegimeAssessmentEntity> findByAssessmentBasisOrderByTradingDateDescAsOfDescCalculatedAtDesc(
            String assessmentBasis, Pageable pageable);
}
