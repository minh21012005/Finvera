package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.StrategySignalEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StrategySignalRepository extends JpaRepository<StrategySignalEntity, UUID> {

    Optional<StrategySignalEntity> findFirstByInstrumentIdAndStrategyCodeAndAsOfTradingDateAndRuleVersionAndCurrentTrue(
            UUID instrumentId, String strategyCode, LocalDate asOfTradingDate, String ruleVersion);

    /**
     * research R-006/data-model.md: the current row per (instrument, strategy)
     * at its own latest as-of trading date — mirrors
     * {@code TechnicalIndicatorResultRepository.findLatestCurrentByInstrumentIdInAndRuleVersion}
     * (Feature 003 R-002), since more than one current row can exist per
     * instrument/strategy across historical as-of dates.
     */
    @Query("""
            SELECT s FROM StrategySignalEntity s
            WHERE s.instrumentId IN :instrumentIds
              AND s.ruleVersion = :ruleVersion
              AND s.current = true
              AND s.asOfTradingDate = (
                  SELECT MAX(s2.asOfTradingDate) FROM StrategySignalEntity s2
                  WHERE s2.instrumentId = s.instrumentId
                    AND s2.strategyCode = s.strategyCode
                    AND s2.ruleVersion = s.ruleVersion
                    AND s2.current = true
              )
            """)
    List<StrategySignalEntity> findLatestCurrentByInstrumentIdInAndRuleVersion(
            @Param("instrumentIds") Collection<UUID> instrumentIds, @Param("ruleVersion") String ruleVersion);
}
