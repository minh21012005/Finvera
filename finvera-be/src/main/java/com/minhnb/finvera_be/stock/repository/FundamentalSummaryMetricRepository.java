package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.FundamentalSummaryMetricEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundamentalSummaryMetricRepository
        extends JpaRepository<FundamentalSummaryMetricEntity, FundamentalSummaryMetricEntity.Key> {

    List<FundamentalSummaryMetricEntity> findBySummaryId(UUID summaryId);
}
