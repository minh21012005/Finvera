package com.minhnb.finvera_be.market.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * The published boundary for the shared {@code ingestion_record} table
 * (plan.md: "the stock module ingests through the same accepted-observation
 * boundary Feature 001 built"). Other modules record and query accepted or
 * rejected inbound records through this interface only, never through
 * {@code market.entity.MarketObservationEntity} or
 * {@code market.repository.MarketObservationRepository} directly.
 */
public interface IngestionRecordService {

    boolean isDuplicate(
            String source, String dataset, String subjectKey, LocalDate tradingDate,
            Instant observedAt, String payloadHash);

    Optional<AcceptedRecord> findLatestAccepted(
            String source, String dataset, String subjectKey, LocalDate tradingDate);

    UUID recordAccepted(
            String source, String dataset, String subjectKey, LocalDate tradingDate, Instant observedAt,
            Instant ingestedAt, String payloadHash, UUID supersedesId);

    UUID recordRejected(
            String source, String dataset, String subjectKey, LocalDate tradingDate, Instant observedAt,
            Instant ingestedAt, String payloadHash, String reasonCode);

    record AcceptedRecord(UUID id, Instant observedAt) {
    }
}
