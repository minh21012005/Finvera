package com.minhnb.finvera_be.market.provider;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/** Read-only provider contract; implementations are replaceable data-source integrations. */
public interface MarketDataProvider {

    ProviderReferenceBatch fetchReferenceData(LocalDate effectiveDate);

    ProviderSnapshotBatch reconcileLatest(LocalDate tradingDate);

    Subscription subscribe(Consumer<ProviderObservation> observer);

    ProviderHealth health();

    record ProviderReferenceBatch(String source, LocalDate effectiveDate, List<IndexCode> indices) {
        public ProviderReferenceBatch {
            indices = List.copyOf(indices);
        }
    }

    record ProviderSnapshotBatch(
            String source,
            LocalDate tradingDate,
            Instant observedAt,
            SessionState sessionState,
            DataStatus dataStatus,
            List<String> reasonCodes,
            List<ProviderObservation> observations) {
        public ProviderSnapshotBatch {
            reasonCodes = List.copyOf(reasonCodes);
            observations = List.copyOf(observations);
        }
    }

    record ProviderObservation(
            IndexCode code,
            BigDecimal level,
            BigDecimal referenceLevel,
            BigDecimal absoluteChange,
            BigDecimal percentageChange,
            Long matchedVolume,
            BigDecimal matchedValueVnd,
            List<String> reasonCodes) {
        public ProviderObservation {
            reasonCodes = List.copyOf(reasonCodes);
        }
    }

    record ProviderHealth(ProviderHealthState state, String reasonCode) {
    }

    enum ProviderHealthState {
        READY, DEGRADED, AUTH_REQUIRED
    }

    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    final class ProviderAuthenticationRequiredException extends RuntimeException {
        public ProviderAuthenticationRequiredException() {
            super("Provider authentication is required");
        }
    }
}
