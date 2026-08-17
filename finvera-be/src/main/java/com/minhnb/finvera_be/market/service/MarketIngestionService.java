package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.entity.MarketIndexSnapshotEntity;
import com.minhnb.finvera_be.market.entity.MarketObservationEntity;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderObservation;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderSnapshotBatch;
import com.minhnb.finvera_be.market.repository.MarketIndexRepository;
import com.minhnb.finvera_be.market.repository.MarketIndexSnapshotRepository;
import com.minhnb.finvera_be.market.repository.MarketObservationRepository;
import com.minhnb.finvera_be.market.service.MarketObservabilityService.MarketDataset;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MarketIngestionService {

    private static final String DATASET = "INDEX";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final MarketObservationRepository observations;
    private final MarketIndexSnapshotRepository snapshots;
    private final MarketIndexRepository indices;
    private final MarketObservabilityService observability;
    private final Clock clock;

    public MarketIngestionService(
            MarketObservationRepository observations,
            MarketIndexSnapshotRepository snapshots,
            MarketIndexRepository indices,
            MarketObservabilityService observability,
            Clock clock) {
        this.observations = observations;
        this.snapshots = snapshots;
        this.indices = indices;
        this.observability = observability;
        this.clock = clock;
    }

    @Transactional
    public IngestionSummary ingest(ProviderSnapshotBatch batch) {
        Objects.requireNonNull(batch, "batch");
        if (batch.source() == null || batch.source().isBlank()) {
            throw new IllegalArgumentException("Provider source must not be blank");
        }

        Instant ingestedAt = clock.instant();
        List<IngestionResult> results = new ArrayList<>(batch.observations().size());
        for (ProviderObservation incoming : batch.observations()) {
            results.add(ingestOne(batch, incoming, ingestedAt));
        }
        IngestionSummary summary = new IngestionSummary(results);
        recordTelemetryAfterCommit(batch, summary);
        return summary;
    }

    private void recordTelemetryAfterCommit(ProviderSnapshotBatch batch, IngestionSummary summary) {
        Runnable recorder = () -> summary.results().forEach(result -> observability.recordIngestion(
                batch.source(), MarketDataset.INDEX, result, batch.observedAt()));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recorder.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recorder.run();
            }
        });
    }

    private IngestionResult ingestOne(
            ProviderSnapshotBatch batch, ProviderObservation incoming, Instant ingestedAt) {
        String subject = incoming.code().name();
        String payloadHash = canonicalHash(batch, incoming);
        boolean duplicate = observations
                .existsBySourceAndDatasetAndSubjectKeyAndTradingDateAndObservedAtAndPayloadHash(
                        batch.source(), DATASET, subject, batch.tradingDate(),
                        batch.observedAt(), payloadHash);
        if (duplicate) {
            return new IngestionResult(incoming.code(), IngestionStatus.DUPLICATE,
                    "DUPLICATE_OBSERVATION", null, null, null);
        }

        Optional<MarketObservationEntity> correctedObservation = observations
                .findFirstBySourceAndDatasetAndSubjectKeyAndTradingDateAndObservedAtAndStatusOrderByIngestedAtDesc(
                        batch.source(), DATASET, subject, batch.tradingDate(),
                        batch.observedAt(), ACCEPTED);
        Optional<MarketObservationEntity> latestAccepted = observations
                .findFirstBySourceAndDatasetAndSubjectKeyAndTradingDateAndStatusOrderByObservedAtDescIngestedAtDesc(
                        batch.source(), DATASET, subject, batch.tradingDate(), ACCEPTED);
        if (correctedObservation.isEmpty() && latestAccepted.isPresent()
                && latestAccepted.orElseThrow().getObservedAt().isAfter(batch.observedAt())) {
            return reject(batch, incoming, ingestedAt, payloadHash, "OUT_OF_ORDER");
        }

        Validation validation = normalize(incoming);
        if (!validation.valid()) {
            return reject(batch, incoming, ingestedAt, payloadHash, validation.reasonCode());
        }

        var index = indices.findByCode(subject);
        if (index.isEmpty()) {
            return reject(batch, incoming, ingestedAt, payloadHash, "INDEX_REFERENCE_NOT_FOUND");
        }

        boolean correction = correctedObservation.isPresent();
        UUID supersededObservationId = correction ? correctedObservation.orElseThrow().getId() : null;
        UUID observationId = UUID.randomUUID();
        var accepted = new MarketObservationEntity(
                observationId, batch.source(), DATASET, subject, batch.tradingDate(),
                batch.observedAt(), batch.observedAt(), ingestedAt, null, payloadHash,
                ACCEPTED, null, supersededObservationId);
        observations.save(accepted);

        Optional<MarketIndexSnapshotEntity> correctedSnapshot = correction
                ? snapshots.findFirstByIndexIdAndTradingDateAndObservedAtOrderByRevisionDesc(
                        index.orElseThrow().getId(), batch.tradingDate(), batch.observedAt())
                : Optional.empty();
        int revision = correctedSnapshot.map(value -> value.getRevision() + 1).orElse(1);
        UUID supersededSnapshotId = correctedSnapshot.map(MarketIndexSnapshotEntity::getId).orElse(null);
        UUID snapshotId = UUID.randomUUID();
        Normalized value = validation.value();
        snapshots.save(new MarketIndexSnapshotEntity(
                snapshotId, index.orElseThrow().getId(), observationId, batch.tradingDate(),
                batch.observedAt(), ingestedAt, batch.sessionState().name(), value.level(),
                value.referenceLevel(), value.absoluteChange(), value.percentageChange(),
                value.matchedVolume(), value.matchedValueVnd(), batch.source(), revision,
                supersededSnapshotId));

        return new IngestionResult(incoming.code(),
                correction ? IngestionStatus.CORRECTED : IngestionStatus.ACCEPTED,
                null, observationId, snapshotId, revision);
    }

    private IngestionResult reject(
            ProviderSnapshotBatch batch,
            ProviderObservation incoming,
            Instant ingestedAt,
            String payloadHash,
            String reasonCode) {
        UUID observationId = UUID.randomUUID();
        observations.save(new MarketObservationEntity(
                observationId, batch.source(), DATASET, incoming.code().name(), batch.tradingDate(),
                batch.observedAt(), batch.observedAt(), ingestedAt, null, payloadHash,
                REJECTED, reasonCode, null));
        return new IngestionResult(incoming.code(), IngestionStatus.REJECTED,
                reasonCode, observationId, null, null);
    }

    private static Validation normalize(ProviderObservation incoming) {
        if (incoming.level() == null) {
            return Validation.rejected("MISSING_INDEX_LEVEL");
        }
        if (incoming.level().signum() < 0) {
            return Validation.rejected("INVALID_INDEX_LEVEL");
        }
        if (incoming.referenceLevel() == null) {
            return Validation.rejected("MISSING_REFERENCE_LEVEL");
        }
        if (incoming.referenceLevel().signum() <= 0) {
            return Validation.rejected("INVALID_REFERENCE_LEVEL");
        }
        if (incoming.matchedVolume() != null && incoming.matchedVolume() < 0) {
            return Validation.rejected("INVALID_MATCHED_VOLUME");
        }
        if (incoming.matchedValueVnd() != null && incoming.matchedValueVnd().signum() < 0) {
            return Validation.rejected("INVALID_MATCHED_VALUE");
        }

        try {
            BigDecimal level = exact(incoming.level(), 18, 6);
            BigDecimal reference = exact(incoming.referenceLevel(), 18, 6);
            BigDecimal matchedValue = incoming.matchedValueVnd() == null
                    ? null : exact(incoming.matchedValueVnd(), 24, 4);
            BigDecimal absoluteChange = level.subtract(reference).setScale(6, RoundingMode.UNNECESSARY);
            BigDecimal percentageChange = absoluteChange.multiply(ONE_HUNDRED)
                    .divide(reference, 6, RoundingMode.HALF_UP);
            if (percentageChange.precision() > 12) {
                return Validation.rejected("INVALID_PERCENTAGE_CHANGE");
            }
            return Validation.accepted(new Normalized(
                    level, reference, absoluteChange, percentageChange,
                    incoming.matchedVolume(), matchedValue));
        } catch (ArithmeticException exception) {
            return Validation.rejected("INVALID_DECIMAL_PRECISION");
        }
    }

    private static BigDecimal exact(BigDecimal value, int precision, int scale) {
        BigDecimal normalized = value.setScale(scale, RoundingMode.UNNECESSARY);
        if (normalized.precision() > precision) {
            throw new ArithmeticException("Decimal precision exceeds storage contract");
        }
        return normalized;
    }

    private static String canonicalHash(ProviderSnapshotBatch batch, ProviderObservation observation) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, batch.source());
            update(digest, DATASET);
            update(digest, observation.code().name());
            update(digest, batch.tradingDate().toString());
            update(digest, batch.observedAt().toString());
            update(digest, batch.sessionState().name());
            update(digest, canonicalDecimal(observation.level()));
            update(digest, canonicalDecimal(observation.referenceLevel()));
            update(digest, observation.matchedVolume() == null
                    ? null : observation.matchedVolume().toString());
            update(digest, canonicalDecimal(observation.matchedValueVnd()));
            observation.reasonCodes().stream().sorted(Comparator.naturalOrder())
                    .forEach(reason -> update(digest, reason));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String canonicalDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    public enum IngestionStatus {
        ACCEPTED, DUPLICATE, REJECTED, CORRECTED
    }

    public record IngestionResult(
            IndexCode code,
            IngestionStatus status,
            String reasonCode,
            UUID observationId,
            UUID snapshotId,
            Integer revision) {
    }

    public record IngestionSummary(List<IngestionResult> results) {
        public IngestionSummary {
            results = List.copyOf(results);
        }
    }

    private record Normalized(
            BigDecimal level,
            BigDecimal referenceLevel,
            BigDecimal absoluteChange,
            BigDecimal percentageChange,
            Long matchedVolume,
            BigDecimal matchedValueVnd) {
    }

    private record Validation(Normalized value, String reasonCode) {
        static Validation accepted(Normalized value) {
            return new Validation(value, null);
        }

        static Validation rejected(String reasonCode) {
            return new Validation(null, reasonCode);
        }

        boolean valid() {
            return value != null;
        }
    }
}
