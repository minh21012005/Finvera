package com.minhnb.finvera_be.market.domain.breadth;

import com.minhnb.finvera_be.market.domain.breadth.BreadthUniversePolicy.InstrumentType;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.AdjustmentStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic breadth-v1 classification over the policy-defined equity universe. */
public final class BreadthCalculator {

    private final BreadthUniversePolicy universePolicy;

    public BreadthCalculator(BreadthUniversePolicy universePolicy) {
        this.universePolicy = Objects.requireNonNull(universePolicy, "universePolicy");
    }

    public Result calculate(List<SecurityInput> inputs) {
        Map<String, SecurityInput> identified = new LinkedHashMap<>();
        List<SecurityInput> unresolved = new ArrayList<>();
        for (SecurityInput input : universePolicy.eligibleSecurities(inputs)) {
            universePolicy.identityKey(input).ifPresentOrElse(
                    identity -> identified.putIfAbsent(identity, input),
                    () -> unresolved.add(input));
        }

        Counts counts = new Counts();
        for (SecurityInput input : identified.values()) {
            counts.add(classify(input));
        }
        for (SecurityInput ignored : unresolved) {
            counts.add(Classification.UNCLASSIFIED);
            counts.addReason("UNRESOLVED_IDENTITY");
        }
        return counts.toResult();
    }

    private static Classification classify(SecurityInput input) {
        if (input.matchedOrClosePrice() == null) {
            return Classification.MISSING_PRICE;
        }
        if (input.officialReferencePrice() == null || input.officialReferencePrice().signum() < 0) {
            return Classification.MISSING_REFERENCE_PRICE;
        }
        int comparison = input.matchedOrClosePrice().compareTo(input.officialReferencePrice());
        return comparison > 0 ? Classification.ADVANCING
                : comparison < 0 ? Classification.DECLINING : Classification.UNCHANGED;
    }

    public record SecurityInput(
            Venue venue,
            String symbol,
            String isin,
            boolean active,
            boolean vn30Member,
            InstrumentType instrumentType,
            BigDecimal matchedOrClosePrice,
            BigDecimal officialReferencePrice,
            AdjustmentStatus adjustmentStatus) {

        public SecurityInput {
            Objects.requireNonNull(venue, "venue");
            Objects.requireNonNull(instrumentType, "instrumentType");
            Objects.requireNonNull(adjustmentStatus, "adjustmentStatus");
        }
    }

    public record Result(int advancing, int declining, int unchanged, int unclassified, int eligible,
                         List<String> reasonCodes) {
        public Result {
            reasonCodes = List.copyOf(reasonCodes);
            if (advancing < 0 || declining < 0 || unchanged < 0 || unclassified < 0
                    || eligible != advancing + declining + unchanged + unclassified) {
                throw new IllegalArgumentException("Breadth counts must reconcile");
            }
        }
    }

    private enum Classification {
        ADVANCING, DECLINING, UNCHANGED, MISSING_PRICE, MISSING_REFERENCE_PRICE, UNCLASSIFIED
    }

    private static final class Counts {
        private int advancing;
        private int declining;
        private int unchanged;
        private int unclassified;
        private final List<String> reasonCodes = new ArrayList<>();

        private void add(Classification classification) {
            switch (classification) {
                case ADVANCING -> advancing++;
                case DECLINING -> declining++;
                case UNCHANGED -> unchanged++;
                case MISSING_PRICE -> {
                    unclassified++;
                    addReason("MISSING_PRICE");
                }
                case MISSING_REFERENCE_PRICE -> {
                    unclassified++;
                    addReason("MISSING_REFERENCE_PRICE");
                }
                case UNCLASSIFIED -> unclassified++;
            }
        }

        private void addReason(String reasonCode) {
            if (!reasonCodes.contains(reasonCode)) {
                reasonCodes.add(reasonCode);
            }
        }

        private Result toResult() {
            int eligible = advancing + declining + unchanged + unclassified;
            return new Result(advancing, declining, unchanged, unclassified, eligible, reasonCodes);
        }
    }
}
