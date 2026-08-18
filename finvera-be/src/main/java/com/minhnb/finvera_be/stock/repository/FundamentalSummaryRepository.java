package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.FundamentalSummaryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundamentalSummaryRepository extends JpaRepository<FundamentalSummaryEntity, UUID> {

    Optional<FundamentalSummaryEntity> findFirstByInstrumentIdAndRuleVersionOrderByAsOfTradingDateDescCalculatedAtDesc(
            UUID instrumentId, String ruleVersion);
}
