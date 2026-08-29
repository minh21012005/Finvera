package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.entity.MarketImportBatchEntity;
import com.minhnb.finvera_be.market.repository.MarketImportBatchRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default JPA-backed implementation of {@link MarketImportBatchService}. */
@Service
public class DefaultMarketImportBatchService implements MarketImportBatchService {

    private final MarketImportBatchRepository importBatches;
    private final Clock clock;

    public DefaultMarketImportBatchService(MarketImportBatchRepository importBatches, Clock clock) {
        this.importBatches = importBatches;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPackageAlreadyImported(String packageSha256) {
        Objects.requireNonNull(packageSha256, "packageSha256");
        return importBatches.existsByPackageSha256(packageSha256);
    }

    @Override
    @Transactional
    public UUID recordAcceptedBatch(AcceptedBatch batch) {
        Objects.requireNonNull(batch, "batch");
        UUID importBatchId = UUID.randomUUID();
        importBatches.save(new MarketImportBatchEntity(importBatchId, batch.contractVersion(), batch.toolName(),
                batch.toolVersion(), batch.upstreamSource(), batch.packageSha256(), batch.rangeStart(),
                batch.rangeEnd(), batch.generatedAt(), clock.instant(), "ACCEPTED", batch.recordCount(), null));
        return importBatchId;
    }
}
