package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.entity.MarketObservationEntity;
import com.minhnb.finvera_be.market.repository.MarketObservationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultIngestionRecordService implements IngestionRecordService {

    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";

    private final MarketObservationRepository observations;

    public DefaultIngestionRecordService(MarketObservationRepository observations) {
        this.observations = observations;
    }

    @Override
    public boolean isDuplicate(
            String source, String dataset, String subjectKey, LocalDate tradingDate,
            Instant observedAt, String payloadHash) {
        return observations.existsBySourceAndDatasetAndSubjectKeyAndTradingDateAndObservedAtAndPayloadHash(
                source, dataset, subjectKey, tradingDate, observedAt, payloadHash);
    }

    @Override
    public Optional<AcceptedRecord> findLatestAccepted(
            String source, String dataset, String subjectKey, LocalDate tradingDate) {
        return observations
                .findFirstBySourceAndDatasetAndSubjectKeyAndTradingDateAndStatusOrderByObservedAtDescIngestedAtDesc(
                        source, dataset, subjectKey, tradingDate, ACCEPTED)
                .map(entity -> new AcceptedRecord(entity.getId(), entity.getObservedAt()));
    }

    @Override
    @Transactional
    public UUID recordAccepted(
            String source, String dataset, String subjectKey, LocalDate tradingDate, Instant observedAt,
            Instant ingestedAt, String payloadHash, UUID supersedesId) {
        UUID id = UUID.randomUUID();
        observations.save(new MarketObservationEntity(id, source, dataset, subjectKey, tradingDate, observedAt,
                observedAt, ingestedAt, null, payloadHash, ACCEPTED, null, supersedesId));
        return id;
    }

    @Override
    @Transactional
    public UUID recordRejected(
            String source, String dataset, String subjectKey, LocalDate tradingDate, Instant observedAt,
            Instant ingestedAt, String payloadHash, String reasonCode) {
        UUID id = UUID.randomUUID();
        observations.save(new MarketObservationEntity(id, source, dataset, subjectKey, tradingDate, observedAt,
                observedAt, ingestedAt, null, payloadHash, REJECTED, reasonCode, null));
        return id;
    }
}
