package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquityDailyBarRepository extends JpaRepository<EquityDailyBarEntity, UUID> {

    Optional<EquityDailyBarEntity> findFirstByInstrumentIdAndTradingDateAndSourceAndCurrentTrue(
            UUID instrumentId, LocalDate tradingDate, String source);

    List<EquityDailyBarEntity> findByInstrumentIdAndSourceAndCurrentTrueAndTradingDateBetweenOrderByTradingDateAsc(
            UUID instrumentId, String source, LocalDate fromInclusive, LocalDate toInclusive);

    Optional<EquityDailyBarEntity> findFirstByInstrumentIdAndSourceAndCurrentTrueOrderByTradingDateDesc(
            UUID instrumentId, String source);

    long countByInstrumentId(UUID instrumentId);
}
