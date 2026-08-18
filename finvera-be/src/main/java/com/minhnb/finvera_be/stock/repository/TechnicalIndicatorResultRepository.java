package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorResultEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnicalIndicatorResultRepository extends JpaRepository<TechnicalIndicatorResultEntity, UUID> {

    Optional<TechnicalIndicatorResultEntity> findFirstByInstrumentIdAndIndicatorCodeAndAsOfTradingDateAndRuleVersionAndCurrentTrue(
            UUID instrumentId, String indicatorCode, LocalDate asOfTradingDate, String ruleVersion);

    List<TechnicalIndicatorResultEntity> findByInstrumentIdAndRuleVersionAndAsOfTradingDateAndCurrentTrue(
            UUID instrumentId, String ruleVersion, LocalDate asOfTradingDate);
}
