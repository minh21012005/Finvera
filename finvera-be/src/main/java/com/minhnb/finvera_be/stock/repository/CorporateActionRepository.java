package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.CorporateActionEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorporateActionRepository extends JpaRepository<CorporateActionEntity, UUID> {

    List<CorporateActionEntity> findByInstrumentIdAndExDateBetweenOrderByExDateAsc(
            UUID instrumentId, LocalDate fromInclusive, LocalDate toInclusive);
}
