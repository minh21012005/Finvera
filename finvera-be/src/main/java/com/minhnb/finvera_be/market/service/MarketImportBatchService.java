package com.minhnb.finvera_be.market.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The published application interface for recording import-batch provenance in
 * the {@code market_import_batch} table the {@code market} module owns
 * (ADR-0007, ARCHITECTURE.md B-5). The {@code stock} module's owner-operated
 * package importers MUST call this interface and MUST NOT depend on
 * {@code market.entity.MarketImportBatchEntity} or
 * {@code market.repository.MarketImportBatchRepository} directly; that boundary
 * is enforced by {@code StockModuleArchitectureTests}. Mirrors the
 * {@link MarketReferenceDataService} precedent.
 */
public interface MarketImportBatchService {

    /** Whether a package with this SHA-256 has already been accepted (idempotent re-import guard). */
    boolean isPackageAlreadyImported(String packageSha256);

    /**
     * Records one accepted canonical package as an immutable provenance row and
     * returns the new batch id for the importer to link its accepted records to.
     */
    UUID recordAcceptedBatch(AcceptedBatch batch);

    /** Provenance of one accepted package, matching the exporter package header fields. */
    record AcceptedBatch(
            String contractVersion,
            String toolName,
            String toolVersion,
            String upstreamSource,
            String packageSha256,
            LocalDate rangeStart,
            LocalDate rangeEnd,
            Instant generatedAt,
            long recordCount) {

        public AcceptedBatch {
            Objects.requireNonNull(contractVersion, "contractVersion");
            Objects.requireNonNull(toolName, "toolName");
            Objects.requireNonNull(toolVersion, "toolVersion");
            Objects.requireNonNull(upstreamSource, "upstreamSource");
            Objects.requireNonNull(packageSha256, "packageSha256");
            Objects.requireNonNull(rangeStart, "rangeStart");
            Objects.requireNonNull(rangeEnd, "rangeEnd");
            Objects.requireNonNull(generatedAt, "generatedAt");
            if (recordCount < 0) {
                throw new IllegalArgumentException("recordCount must be non-negative");
            }
        }
    }
}
