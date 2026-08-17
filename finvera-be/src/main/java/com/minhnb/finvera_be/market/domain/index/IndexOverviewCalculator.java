package com.minhnb.finvera_be.market.domain.index;

import com.minhnb.finvera_be.market.domain.index.IndexOverview.IndexFact;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Direction;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IndexOverviewCalculator {

    private static final List<IndexCode> STABLE_ORDER = List.of(
            IndexCode.VN_INDEX, IndexCode.VN30, IndexCode.HNX_INDEX, IndexCode.UPCOM_INDEX);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public IndexOverview calculate(SnapshotInput snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<IndexCode, IndexInput> byCode = new EnumMap<>(IndexCode.class);
        for (IndexInput input : snapshot.indices()) {
            if (byCode.put(input.code(), input) != null) {
                throw new IllegalArgumentException("Duplicate index: " + input.code());
            }
        }

        List<IndexFact> facts = new ArrayList<>(STABLE_ORDER.size());
        for (IndexCode code : STABLE_ORDER) {
            facts.add(toFact(code, byCode.get(code), snapshot.dataStatus()));
        }
        return new IndexOverview(
                snapshot.tradingDate(), snapshot.observedAt(), snapshot.sessionState(),
                snapshot.dataStatus(), snapshot.revision(), snapshot.source(), facts);
    }

    private IndexFact toFact(IndexCode code, IndexInput input, DataStatus snapshotStatus) {
        Venue venue = venueFor(code);
        if (input == null || input.level() == null) {
            List<String> reasons = input == null || input.reasonCodes().isEmpty()
                    ? List.of("MISSING_INDEX") : input.reasonCodes();
            return new IndexFact(code, venue, null, null, null, null, null, null,
                    DataStatus.UNAVAILABLE, reasons);
        }

        BigDecimal absoluteChange = null;
        BigDecimal percentageChange = null;
        Direction direction = null;
        DataStatus status = snapshotStatus;
        List<String> reasons = input.reasonCodes();
        if (input.referenceLevel() == null || input.referenceLevel().signum() <= 0) {
            status = DataStatus.mostActionable(status, DataStatus.PARTIAL);
            if (reasons.isEmpty()) {
                reasons = List.of("MISSING_REFERENCE_LEVEL");
            }
        } else {
            absoluteChange = input.level().subtract(input.referenceLevel()).setScale(6, RoundingMode.UNNECESSARY);
            percentageChange = absoluteChange.multiply(ONE_HUNDRED)
                    .divide(input.referenceLevel(), 6, RoundingMode.HALF_UP);
            direction = absoluteChange.signum() > 0
                    ? Direction.UP : absoluteChange.signum() < 0 ? Direction.DOWN : Direction.UNCHANGED;
        }

        return new IndexFact(
                code, venue, input.level(), absoluteChange, percentageChange,
                input.matchedVolume(), input.matchedValueVnd(), direction, status, reasons);
    }

    private static Venue venueFor(IndexCode code) {
        return switch (code) {
            case VN_INDEX, VN30 -> Venue.HOSE;
            case HNX_INDEX -> Venue.HNX;
            case UPCOM_INDEX -> Venue.UPCOM;
        };
    }

    public record SnapshotInput(
            LocalDate tradingDate,
            Instant observedAt,
            SessionState sessionState,
            DataStatus dataStatus,
            long revision,
            String source,
            List<IndexInput> indices) {

        public SnapshotInput {
            Objects.requireNonNull(tradingDate, "tradingDate");
            Objects.requireNonNull(observedAt, "observedAt");
            Objects.requireNonNull(sessionState, "sessionState");
            Objects.requireNonNull(dataStatus, "dataStatus");
            indices = List.copyOf(indices);
        }
    }

    public record IndexInput(
            IndexCode code,
            BigDecimal level,
            BigDecimal referenceLevel,
            Long matchedVolume,
            BigDecimal matchedValueVnd,
            List<String> reasonCodes) {

        public IndexInput {
            Objects.requireNonNull(code, "code");
            reasonCodes = List.copyOf(reasonCodes);
        }
    }
}
