package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.ValuationAssessmentEntity;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValuationAssessmentRepository extends JpaRepository<ValuationAssessmentEntity, UUID> {

    Optional<ValuationAssessmentEntity> findFirstByInstrumentIdAndRuleVersionAndAsOfTradingDateAndCurrentTrue(
            UUID instrumentId, String ruleVersion, LocalDate asOfTradingDate);

    Optional<ValuationAssessmentEntity> findFirstByInstrumentIdAndRuleVersionAndCurrentTrueOrderByAsOfTradingDateDesc(
            UUID instrumentId, String ruleVersion);
}
