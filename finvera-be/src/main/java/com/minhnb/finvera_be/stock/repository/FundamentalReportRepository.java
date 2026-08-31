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

    /** Feature 018 (contract vci-fundamentals-v1 I-1): the current row for a period whatever its source. */
    Optional<FundamentalReportEntity> findFirstByInstrumentIdAndPeriodTypeAndFiscalYearAndFiscalQuarterAndReportKindAndCurrentTrue(
            UUID instrumentId, String periodType, short fiscalYear, Short fiscalQuarter, String reportKind);

    Optional<FundamentalReportEntity> findFirstByInstrumentIdAndCurrentTrueOrderByPeriodEndDesc(UUID instrumentId);

    /** Feature 018 source retirement: every current row still attributed to a source. */
    List<FundamentalReportEntity> findAllBySourceAndCurrentTrue(String source);

    /** Feature 018 default retirement: current rows whose source is not the primary fundamentals source. */
    List<FundamentalReportEntity> findAllByCurrentTrueAndSourceNot(String source);

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
