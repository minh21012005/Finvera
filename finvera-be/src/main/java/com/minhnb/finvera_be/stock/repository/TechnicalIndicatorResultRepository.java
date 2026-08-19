package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorResultEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TechnicalIndicatorResultRepository extends JpaRepository<TechnicalIndicatorResultEntity, UUID> {

    Optional<TechnicalIndicatorResultEntity> findFirstByInstrumentIdAndIndicatorCodeAndAsOfTradingDateAndRuleVersionAndCurrentTrue(
            UUID instrumentId, String indicatorCode, LocalDate asOfTradingDate, String ruleVersion);

    List<TechnicalIndicatorResultEntity> findByInstrumentIdAndRuleVersionAndAsOfTradingDateAndCurrentTrue(
            UUID instrumentId, String ruleVersion, LocalDate asOfTradingDate);

    /**
     * Feature 003 research R-002: the latest current row per (instrument,
     * indicator) for the given rule version, across every candidate in
     * {@code instrumentIds}, in one bulk query. {@code current} scopes a
     * partial-unique index per {@code (instrument, indicator, as_of_trading_date,
     * rule_version)} (data-model.md), so more than one current row can exist
     * per instrument/indicator across historical as-of dates; this query
     * picks the one with the greatest {@code as_of_trading_date}.
     */
    @Query("""
            SELECT t FROM TechnicalIndicatorResultEntity t
            WHERE t.instrumentId IN :instrumentIds
              AND t.ruleVersion = :ruleVersion
              AND t.current = true
              AND t.asOfTradingDate = (
                  SELECT MAX(t2.asOfTradingDate) FROM TechnicalIndicatorResultEntity t2
                  WHERE t2.instrumentId = t.instrumentId
                    AND t2.indicatorCode = t.indicatorCode
                    AND t2.ruleVersion = t.ruleVersion
                    AND t2.current = true
              )
            """)
    List<TechnicalIndicatorResultEntity> findLatestCurrentByInstrumentIdInAndRuleVersion(
            @Param("instrumentIds") Collection<UUID> instrumentIds, @Param("ruleVersion") String ruleVersion);
}
