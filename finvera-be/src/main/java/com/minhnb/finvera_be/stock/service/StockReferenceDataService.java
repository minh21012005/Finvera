package com.minhnb.finvera_be.stock.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The published read-only interface other modules (such as {@code portfolio})
 * use for stock reference data, accepted bars, sector classification, and
 * current strategy signals owned by the {@code stock} module.
 *
 * <p>Other modules MUST call only this interface and MUST NOT depend on
 * {@code stock.entity} or {@code stock.repository} directly; that boundary
 * is enforced by architecture tests.
 */
public interface StockReferenceDataService {

    /**
     * Finds the latest current strategy signals for the given instruments under
     * the active rule version ("strategy-signal-v1").
     */
    List<SignalReference> findCurrentSignalsForInstruments(Collection<UUID> instrumentIds);

    /**
     * Finds the latest accepted daily bar for an instrument.
     */
    Optional<DailyBarReference> findLatestDailyBar(UUID instrumentId);

    /**
     * Bulk resolution of latest accepted daily bars for the given instruments.
     */
    List<DailyBarReference> findLatestDailyBars(Collection<UUID> instrumentIds);

    /**
     * Finds accepted daily bars for an instrument within a date range [fromInclusive, toInclusive],
     * ordered by trading date ASC.
     */
    List<DailyBarReference> findDailyBars(UUID instrumentId, LocalDate fromInclusive, LocalDate toInclusive);

    /**
     * Finds active equity profile (including sector name) for an instrument.
     */
    Optional<EquityProfileReference> findEquityProfile(UUID instrumentId);

    /**
     * Bulk resolution of active equity profiles for the given instruments.
     */
    List<EquityProfileReference> findEquityProfiles(Collection<UUID> instrumentIds);

    record SignalReference(
            UUID id,
            UUID instrumentId,
            String strategyCode,
            String ruleVersion,
            LocalDate asOfTradingDate,
            String direction,
            BigDecimal entryLow,
            BigDecimal entryHigh,
            BigDecimal stopLoss,
            BigDecimal target1,
            BigDecimal target2,
            BigDecimal riskReward,
            Integer riskScore,
            String riskLevel,
            Instant calculatedAt) {
    }

    record DailyBarReference(
            UUID id,
            UUID instrumentId,
            LocalDate tradingDate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            Long volume,
            BigDecimal valueVnd,
            String source,
            Instant acceptedAt) {
    }

    record EquityProfileReference(
            UUID instrumentId,
            String companyNameVi,
            String companyNameEn,
            UUID sectorReferenceId,
            String sectorDisplayNameVi,
            String sectorDisplayNameEn) {
    }
}
