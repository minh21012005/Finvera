package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.FundamentalReportMetricEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundamentalReportMetricRepository
        extends JpaRepository<FundamentalReportMetricEntity, FundamentalReportMetricEntity.Key> {

    List<FundamentalReportMetricEntity> findByReportId(UUID reportId);

    /** Bulk fetch for the own-history series (Q-28): one query per instrument, not per report. */
    List<FundamentalReportMetricEntity> findByReportIdIn(java.util.Collection<UUID> reportIds);
}
