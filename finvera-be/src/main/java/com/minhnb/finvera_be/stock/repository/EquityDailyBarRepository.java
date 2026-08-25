package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquityDailyBarRepository extends JpaRepository<EquityDailyBarEntity, UUID> {

    String DEPRECATED_TCBS_STOCK_SOURCE = "TCBS_IFLASH_STOCK_DATA";

    Optional<EquityDailyBarEntity> findFirstByInstrumentIdAndTradingDateAndSourceAndCurrentTrue(
            UUID instrumentId, LocalDate tradingDate, String source);

    List<EquityDailyBarEntity> findByInstrumentIdAndSourceAndCurrentTrueAndTradingDateBetweenOrderByTradingDateAsc(
            UUID instrumentId, String source, LocalDate fromInclusive, LocalDate toInclusive);

    Optional<EquityDailyBarEntity> findFirstByInstrumentIdAndSourceAndCurrentTrueOrderByTradingDateDesc(
            UUID instrumentId, String source);

    @Query("""
            select b from EquityDailyBarEntity b
            where b.instrumentId = :instrumentId
              and b.current = true
              and b.source <> 'TCBS_IFLASH_STOCK_DATA'
              and b.tradingDate between :fromInclusive and :toInclusive
            order by b.tradingDate asc
            """)
    List<EquityDailyBarEntity> findByInstrumentIdAndCurrentTrueAndTradingDateBetweenOrderByTradingDateAsc(
            @Param("instrumentId") UUID instrumentId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toInclusive") LocalDate toInclusive);

    @Query(value = """
            SELECT id, instrument_id, ingestion_record_id, import_batch_id, trading_date, open_price,
                   high_price, low_price, close_price, reference_price, adjusted_close, adjustment_factor, adjustment_status,
                   volume, value_vnd, source, observed_at, accepted_at, revision, is_current, supersedes_id,
                   quality_reason
            FROM equity_daily_bar
            WHERE instrument_id = :instrumentId
              AND is_current = true
              AND source <> 'TCBS_IFLASH_STOCK_DATA'
            ORDER BY trading_date DESC, accepted_at DESC
            limit 1
            """, nativeQuery = true)
    Optional<EquityDailyBarEntity> findFirstByInstrumentIdAndCurrentTrueOrderByTradingDateDescAcceptedAtDesc(
            @Param("instrumentId") UUID instrumentId);

    @Query(value = """
            SELECT id, instrument_id, ingestion_record_id, import_batch_id, trading_date, open_price,
                   high_price, low_price, close_price, reference_price, adjusted_close, adjustment_factor, adjustment_status,
                   volume, value_vnd, source, observed_at, accepted_at, revision, is_current, supersedes_id,
                   quality_reason
            FROM equity_daily_bar
            WHERE instrument_id = :instrumentId
              AND is_current = true
              AND source <> 'TCBS_IFLASH_STOCK_DATA'
              AND trading_date < :beforeExclusive
            ORDER BY trading_date DESC
            limit 1
            """, nativeQuery = true)
    Optional<EquityDailyBarEntity> findFirstByInstrumentIdAndCurrentTrueAndTradingDateBeforeOrderByTradingDateDesc(
            @Param("instrumentId") UUID instrumentId,
            @Param("beforeExclusive") LocalDate beforeExclusive);

    long countByInstrumentId(UUID instrumentId);

    @Query("""
            select b from EquityDailyBarEntity b
            where b.instrumentId = :instrumentId
              and b.current = true
              and b.source <> 'TCBS_IFLASH_STOCK_DATA'
            order by b.tradingDate asc
            """)
    List<EquityDailyBarEntity> findByInstrumentIdAndCurrentTrueOrderByTradingDateAsc(
            @Param("instrumentId") UUID instrumentId);

    @Query(value = """
            SELECT id, instrument_id, ingestion_record_id, import_batch_id, trading_date, open_price,
                   high_price, low_price, close_price, reference_price, adjusted_close, adjustment_factor, adjustment_status,
                   volume, value_vnd, source, observed_at, accepted_at, revision, is_current, supersedes_id,
                   quality_reason
            FROM equity_daily_bar
            WHERE instrument_id = :instrumentId
              AND trading_date = :tradingDate
              AND is_current = true
              AND source LIKE :sourcePrefix
            ORDER BY accepted_at DESC
            limit 1
            """, nativeQuery = true)
    Optional<EquityDailyBarEntity> findLatestCurrentByInstrumentIdAndTradingDateAndSourcePrefix(
            @Param("instrumentId") UUID instrumentId,
            @Param("tradingDate") LocalDate tradingDate,
            @Param("sourcePrefix") String sourcePrefix);

    /**
     * Feature 003 research R-002: one bulk fetch covering both the "latest
     * session" fields (Price category) and the Breakout 21-session lookback
     * for every candidate in {@code instrumentIds} — the most recent
     * {@code maxBarsPerInstrument} current bars per instrument, regardless of
     * how long ago they were observed.
     *
     * <p>An earlier version of this query bounded the fetch by a fixed
     * calendar-day window ("last 90 days") instead of a per-instrument bar
     * count. That silently dropped a stock whose last accepted session was
     * older than the window from Price/Market filtering entirely — not
     * "excluded with a reason" (S-4), just invisible — which is exactly the
     * silent-drop behavior {@code screener-v1.md} S-4 forbids. A suspended or
     * thinly-traded stock's last accepted price is still a real fact Feature
     * 002's own stock detail page would show (with its true staleness
     * disclosed); the screener must be able to find it too. A native query
     * with {@code ROW_NUMBER() OVER (PARTITION BY instrument_id ...)} is used
     * because JPQL has no per-group "top N" construct.
     */
    @Query(value = """
            WITH ranked AS (
                SELECT b.*, ROW_NUMBER() OVER (
                    PARTITION BY b.instrument_id ORDER BY b.trading_date DESC
                ) AS rn
                FROM equity_daily_bar b
                WHERE b.instrument_id IN (:instrumentIds)
                  AND b.is_current = true
                  AND b.source <> 'TCBS_IFLASH_STOCK_DATA'
            )
            SELECT id, instrument_id, ingestion_record_id, import_batch_id, trading_date, open_price,
                   high_price, low_price, close_price, reference_price, adjusted_close, adjustment_factor, adjustment_status,
                   volume, value_vnd, source, observed_at, accepted_at, revision, is_current, supersedes_id,
                   quality_reason
            FROM ranked
            WHERE rn <= :maxBarsPerInstrument
            ORDER BY instrument_id ASC, trading_date ASC
            """, nativeQuery = true)
    List<EquityDailyBarEntity> findLatestNCurrentByInstrumentIdIn(
            @Param("instrumentIds") Collection<UUID> instrumentIds,
            @Param("maxBarsPerInstrument") int maxBarsPerInstrument);

    @Modifying
    @Query(value = """
            UPDATE equity_daily_bar current_bar
            SET supersedes_id = NULL
            WHERE current_bar.instrument_id = :instrumentId
              AND current_bar.source = :source
              AND current_bar.supersedes_id IN (
                  SELECT old_bar.id
                  FROM equity_daily_bar old_bar
                  WHERE old_bar.instrument_id = :instrumentId
                    AND old_bar.source = :source
                    AND old_bar.is_current = false
              )
            """, nativeQuery = true)
    int clearSupersededDailyBarLinks(
            @Param("instrumentId") UUID instrumentId,
            @Param("source") String source);

    @Modifying
    @Query(value = """
            DELETE FROM equity_daily_bar old_bar
            WHERE old_bar.instrument_id = :instrumentId
              AND old_bar.source = :source
              AND old_bar.is_current = false
              AND NOT EXISTS (
                  SELECT 1 FROM valuation_assessment_input input
                  WHERE input.daily_bar_id = old_bar.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM strategy_signal_input input
                  WHERE input.daily_bar_id = old_bar.id
              )
            """, nativeQuery = true)
    int deleteUnreferencedSupersededDailyBars(
            @Param("instrumentId") UUID instrumentId,
            @Param("source") String source);
}
