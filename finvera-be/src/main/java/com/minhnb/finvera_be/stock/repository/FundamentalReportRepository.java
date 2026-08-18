package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.FundamentalReportEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundamentalReportRepository extends JpaRepository<FundamentalReportEntity, UUID> {

    Optional<FundamentalReportEntity> findFirstByInstrumentIdAndPeriodTypeAndFiscalYearAndFiscalQuarterAndReportKindAndSourceAndCurrentTrue(
            UUID instrumentId, String periodType, short fiscalYear, Short fiscalQuarter, String reportKind,
            String source);

    Optional<FundamentalReportEntity> findFirstByInstrumentIdAndCurrentTrueOrderByPeriodEndDesc(UUID instrumentId);
}
