package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.ValuationAssessmentEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ValuationAssessmentRepository extends JpaRepository<ValuationAssessmentEntity, UUID> {

    Optional<ValuationAssessmentEntity> findFirstByInstrumentIdAndRuleVersionAndAsOfTradingDateAndCurrentTrue(
            UUID instrumentId, String ruleVersion, LocalDate asOfTradingDate);

    Optional<ValuationAssessmentEntity> findFirstByInstrumentIdAndRuleVersionAndCurrentTrueOrderByAsOfTradingDateDesc(
            UUID instrumentId, String ruleVersion);

    /**
     * Feature 003 research R-002: the latest current assessment per
     * instrument for the given rule version, across every candidate in
     * {@code instrumentIds}, in one bulk query. An instrument absent from
     * the result never had a published-or-withheld assessment computed at
     * all; the screener treats that the same as a withheld one (S-4).
     */
    @Query("""
            SELECT v FROM ValuationAssessmentEntity v
            WHERE v.instrumentId IN :instrumentIds
              AND v.ruleVersion = :ruleVersion
              AND v.current = true
              AND v.asOfTradingDate = (
                  SELECT MAX(v2.asOfTradingDate) FROM ValuationAssessmentEntity v2
                  WHERE v2.instrumentId = v.instrumentId
                    AND v2.ruleVersion = v.ruleVersion
                    AND v2.current = true
              )
            """)
    List<ValuationAssessmentEntity> findLatestCurrentByInstrumentIdInAndRuleVersion(
            @Param("instrumentIds") Collection<UUID> instrumentIds, @Param("ruleVersion") String ruleVersion);
}
