package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.FundamentalSummaryEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FundamentalSummaryRepository extends JpaRepository<FundamentalSummaryEntity, UUID> {

    Optional<FundamentalSummaryEntity> findFirstByInstrumentIdAndRuleVersionOrderByAsOfTradingDateDescCalculatedAtDesc(
            UUID instrumentId, String ruleVersion);

    /**
     * Feature 003 research R-002: the newest {@code (as_of_trading_date,
     * calculated_at)} row per instrument for the given rule version, across
     * every candidate in {@code instrumentIds}, in one bulk query.
     * {@code fundamental_summary} has no {@code is_current} flag
     * (data-model.md); "current" is defined by recency, matching
     * {@code FundamentalReportService}'s single-instrument convention.
     */
    @Query("""
            SELECT s FROM FundamentalSummaryEntity s
            WHERE s.instrumentId IN :instrumentIds
              AND s.ruleVersion = :ruleVersion
              AND s.calculatedAt = (
                  SELECT MAX(s2.calculatedAt) FROM FundamentalSummaryEntity s2
                  WHERE s2.instrumentId = s.instrumentId
                    AND s2.ruleVersion = s.ruleVersion
                    AND s2.asOfTradingDate = (
                        SELECT MAX(s3.asOfTradingDate) FROM FundamentalSummaryEntity s3
                        WHERE s3.instrumentId = s.instrumentId
                          AND s3.ruleVersion = s.ruleVersion
                    )
              )
            """)
    List<FundamentalSummaryEntity> findLatestByInstrumentIdInAndRuleVersion(
            @Param("instrumentIds") Collection<UUID> instrumentIds, @Param("ruleVersion") String ruleVersion);
}
