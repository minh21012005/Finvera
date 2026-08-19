package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquityDailyBarRepository extends JpaRepository<EquityDailyBarEntity, UUID> {

    Optional<EquityDailyBarEntity> findFirstByInstrumentIdAndTradingDateAndSourceAndCurrentTrue(
            UUID instrumentId, LocalDate tradingDate, String source);

    List<EquityDailyBarEntity> findByInstrumentIdAndSourceAndCurrentTrueAndTradingDateBetweenOrderByTradingDateAsc(
            UUID instrumentId, String source, LocalDate fromInclusive, LocalDate toInclusive);

    Optional<EquityDailyBarEntity> findFirstByInstrumentIdAndSourceAndCurrentTrueOrderByTradingDateDesc(
            UUID instrumentId, String source);

    List<EquityDailyBarEntity> findByInstrumentIdAndCurrentTrueAndTradingDateBetweenOrderByTradingDateAsc(
            UUID instrumentId, LocalDate fromInclusive, LocalDate toInclusive);

    Optional<EquityDailyBarEntity> findFirstByInstrumentIdAndCurrentTrueOrderByTradingDateDescAcceptedAtDesc(
            UUID instrumentId);

    Optional<EquityDailyBarEntity> findFirstByInstrumentIdAndCurrentTrueAndTradingDateBeforeOrderByTradingDateDesc(
            UUID instrumentId, LocalDate beforeExclusive);

    long countByInstrumentId(UUID instrumentId);

    List<EquityDailyBarEntity> findByInstrumentIdAndCurrentTrueOrderByTradingDateAsc(UUID instrumentId);

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
                WHERE b.instrument_id IN (:instrumentIds) AND b.is_current = true
            )
            SELECT id, instrument_id, ingestion_record_id, import_batch_id, trading_date, open_price,
                   high_price, low_price, close_price, adjusted_close, adjustment_factor, adjustment_status,
                   volume, value_vnd, source, observed_at, accepted_at, revision, is_current, supersedes_id,
                   quality_reason
            FROM ranked
            WHERE rn <= :maxBarsPerInstrument
            ORDER BY instrument_id ASC, trading_date ASC
            """, nativeQuery = true)
    List<EquityDailyBarEntity> findLatestNCurrentByInstrumentIdIn(
            @Param("instrumentIds") Collection<UUID> instrumentIds,
            @Param("maxBarsPerInstrument") int maxBarsPerInstrument);
}
