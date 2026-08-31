package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.FundamentalReportEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FundamentalReportRepository extends JpaRepository<FundamentalReportEntity, UUID> {

    Optional<FundamentalReportEntity> findFirstByInstrumentIdAndPeriodTypeAndFiscalYearAndFiscalQuarterAndReportKindAndSourceAndCurrentTrue(
            UUID instrumentId, String periodType, short fiscalYear, Short fiscalQuarter, String reportKind,
            String source);

    Optional<FundamentalReportEntity> findFirstByInstrumentIdAndCurrentTrueOrderByPeriodEndDesc(UUID instrumentId);

    java.util.List<FundamentalReportEntity> findAllByInstrumentIdAndCurrentTrueOrderByPeriodEndDesc(UUID instrumentId);

    /** Q-48: when the newest current report of an instrument was accepted (valuation warmup staleness check). */
    @Query("""
            select r.instrumentId, max(r.acceptedAt)
            from FundamentalReportEntity r
            where r.current = true
              and r.instrumentId in :instrumentIds
            group by r.instrumentId
            """)
    List<Object[]> findLatestAcceptedAtByInstrumentIdIn(@Param("instrumentIds") Collection<UUID> instrumentIds);
}
