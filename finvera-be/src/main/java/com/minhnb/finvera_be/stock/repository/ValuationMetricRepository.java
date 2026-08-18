package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.ValuationMetricEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValuationMetricRepository
        extends JpaRepository<ValuationMetricEntity, ValuationMetricEntity.Key> {

    List<ValuationMetricEntity> findByAssessmentId(UUID assessmentId);
}
