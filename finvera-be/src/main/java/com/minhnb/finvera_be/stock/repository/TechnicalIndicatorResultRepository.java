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

    /**
     * Feature 004 research R-002/T006: the latest {@code maxRowsPerGroup}
     * current rows per (instrument, indicator) for the given rule version, in
     * one bulk query — used for the "current + immediately preceding trading
     * date" pair the three crossing strategies need ({@code maxRowsPerGroup =
     * 2}). Same {@code ROW_NUMBER() OVER (PARTITION BY ...)} technique as
     * {@code EquityDailyBarRepository.findLatestNCurrentByInstrumentIdIn}
     * (Feature 003), since JPQL has no per-group "top N" construct.
     */
    @Query(value = """
            WITH ranked AS (
                SELECT r.*, ROW_NUMBER() OVER (
                    PARTITION BY r.instrument_id, r.indicator_code ORDER BY r.as_of_trading_date DESC
                ) AS rn
                FROM technical_indicator_result r
                WHERE r.instrument_id IN (:instrumentIds)
                  AND r.rule_version = :ruleVersion
                  AND r.is_current = true
            )
            SELECT id, instrument_id, indicator_code, rule_version, as_of_trading_date, window_start_date,
                   window_end_date, input_bar_count, input_set_hash, adjustment_status, data_status,
                   quality_reason, calculated_at, is_current, supersedes_id
            FROM ranked
            WHERE rn <= :maxRowsPerGroup
            ORDER BY instrument_id ASC, indicator_code ASC, as_of_trading_date DESC
            """, nativeQuery = true)
    List<TechnicalIndicatorResultEntity> findLatestNCurrentByInstrumentIdInAndRuleVersion(
            @Param("instrumentIds") Collection<UUID> instrumentIds, @Param("ruleVersion") String ruleVersion,
            @Param("maxRowsPerGroup") int maxRowsPerGroup);

    /**
     * Feature 004 research R-004/T006: the trailing {@code maxRowsPerInstrument}
     * current rows for exactly one indicator (the ATR risk factor's own
     * trailing-250-session average) across every instrument in
     * {@code instrumentIds}, filtered at the database rather than fetching
     * every indicator — only called for the (typically small) set of
     * instruments a strategy has actually triggered on, per plan.md's
     * ownership note that risk-factor fetches happen after a trigger, not for
     * every scanned candidate.
     */
    @Query(value = """
            WITH ranked AS (
                SELECT r.*, ROW_NUMBER() OVER (
                    PARTITION BY r.instrument_id ORDER BY r.as_of_trading_date DESC
                ) AS rn
                FROM technical_indicator_result r
                WHERE r.instrument_id IN (:instrumentIds)
                  AND r.rule_version = :ruleVersion
                  AND r.indicator_code = :indicatorCode
                  AND r.is_current = true
            )
            SELECT id, instrument_id, indicator_code, rule_version, as_of_trading_date, window_start_date,
                   window_end_date, input_bar_count, input_set_hash, adjustment_status, data_status,
                   quality_reason, calculated_at, is_current, supersedes_id
            FROM ranked
            WHERE rn <= :maxRowsPerInstrument
            ORDER BY instrument_id ASC, as_of_trading_date DESC
            """, nativeQuery = true)
    List<TechnicalIndicatorResultEntity> findLatestNCurrentByInstrumentIdInAndRuleVersionAndIndicatorCode(
            @Param("instrumentIds") Collection<UUID> instrumentIds, @Param("ruleVersion") String ruleVersion,
            @Param("indicatorCode") String indicatorCode, @Param("maxRowsPerInstrument") int maxRowsPerInstrument);
}
