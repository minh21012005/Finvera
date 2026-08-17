package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketObservationEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketObservationRepository extends JpaRepository<MarketObservationEntity, UUID> {

    boolean existsBySourceAndDatasetAndSubjectKeyAndTradingDateAndObservedAtAndPayloadHash(
            String source, String dataset, String subjectKey, LocalDate tradingDate,
            Instant observedAt, String payloadHash);

    Optional<MarketObservationEntity>
            findFirstBySourceAndDatasetAndSubjectKeyAndTradingDateAndStatusOrderByObservedAtDescIngestedAtDesc(
                    String source, String dataset, String subjectKey, LocalDate tradingDate, String status);

    Optional<MarketObservationEntity>
            findFirstBySourceAndDatasetAndSubjectKeyAndTradingDateAndObservedAtAndStatusOrderByIngestedAtDesc(
                    String source, String dataset, String subjectKey, LocalDate tradingDate,
                    Instant observedAt, String status);
}
