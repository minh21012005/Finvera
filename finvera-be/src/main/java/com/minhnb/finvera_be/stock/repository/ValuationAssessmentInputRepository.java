package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.ValuationAssessmentInputEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValuationAssessmentInputRepository
        extends JpaRepository<ValuationAssessmentInputEntity, ValuationAssessmentInputEntity.Key> {

    List<ValuationAssessmentInputEntity> findByAssessmentId(UUID assessmentId);
}
