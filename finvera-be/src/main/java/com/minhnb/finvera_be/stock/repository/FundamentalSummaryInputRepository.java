package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.FundamentalSummaryInputEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundamentalSummaryInputRepository
        extends JpaRepository<FundamentalSummaryInputEntity, FundamentalSummaryInputEntity.Key> {

    List<FundamentalSummaryInputEntity> findBySummaryId(UUID summaryId);
}
