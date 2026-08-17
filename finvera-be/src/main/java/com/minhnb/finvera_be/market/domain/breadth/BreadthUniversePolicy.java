package com.minhnb.finvera_be.market.domain.breadth;

import com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator.SecurityInput;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Versioned eligibility and identity policy for the consolidated equity breadth universe. */
public final class BreadthUniversePolicy {

    public static final String VERSION = "breadth-universe-v1";

    public List<SecurityInput> eligibleSecurities(List<SecurityInput> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        return inputs.stream().filter(this::isEligible).toList();
    }

    public boolean isEligible(SecurityInput input) {
        Objects.requireNonNull(input, "input");
        return input.active() && input.instrumentType() == InstrumentType.COMMON_EQUITY;
    }

    /** ISIN is canonical; a venue-qualified symbol is only a fallback identity. */
    public Optional<String> identityKey(SecurityInput input) {
        Objects.requireNonNull(input, "input");
        if (hasText(input.isin())) {
            return Optional.of("ISIN:" + input.isin().trim());
        }
        if (hasText(input.symbol())) {
            return Optional.of("VENUE_SYMBOL:" + input.venue().name() + ":" + input.symbol().trim());
        }
        return Optional.empty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public enum InstrumentType {
        COMMON_EQUITY,
        ETF,
        FUND,
        WARRANT,
        BOND,
        DERIVATIVE
    }
}
