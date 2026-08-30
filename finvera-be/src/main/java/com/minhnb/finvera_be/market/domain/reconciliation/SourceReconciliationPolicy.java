package com.minhnb.finvera_be.market.domain.reconciliation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Exact, versioned policy for overlapping TCBS and Vnstock raw observations.
 *
 * <p>v2 (2026-08-30, Feature 002 research R-017): the reference price is compared
 * only when <em>both</em> sources carry one. Vnstock/KBS completed bars carry no
 * historical reference price (R-015), so v1's exact-equality test on a null
 * reference would have flagged every overlapping date as a conflict — which is
 * why the caller had been passing the open price in the reference slot (Q-25).
 * Close prices are still compared exactly at scale 6.
 */
public final class SourceReconciliationPolicy {
    public static final String VERSION = "tcbs-vnstock-reconciliation-v2";

    public Decision reconcile(Fact tcbs, Fact vnstock) {
        if (tcbs == null && vnstock == null) return Decision.UNAVAILABLE;
        if (tcbs == null) return Decision.VNSTOCK_HISTORICAL_BOOTSTRAP;
        if (vnstock == null) return Decision.TCBS_CANONICAL;
        if (tcbs.adjustmentStatus() != AdjustmentStatus.RAW || vnstock.adjustmentStatus() != AdjustmentStatus.RAW)
            return Decision.NON_COMPARABLE;
        boolean closesAgree = exact(tcbs.close(), vnstock.close());
        boolean referencesComparable = tcbs.reference() != null && vnstock.reference() != null;
        boolean referencesAgree = !referencesComparable || exact(tcbs.reference(), vnstock.reference());
        return closesAgree && referencesAgree ? Decision.TCBS_CANONICAL : Decision.SOURCE_CONFLICT;
    }

    private static boolean exact(BigDecimal left, BigDecimal right) {
        return left != null && right != null
                && left.setScale(6, RoundingMode.UNNECESSARY).compareTo(right.setScale(6, RoundingMode.UNNECESSARY)) == 0;
    }

    public record Fact(BigDecimal close, BigDecimal reference, AdjustmentStatus adjustmentStatus) {
        public Fact { Objects.requireNonNull(adjustmentStatus); }
    }
    public enum AdjustmentStatus { RAW, PROVIDER_ADJUSTED, UNKNOWN }
    public enum Decision { TCBS_CANONICAL, VNSTOCK_HISTORICAL_BOOTSTRAP, SOURCE_CONFLICT, NON_COMPARABLE, UNAVAILABLE }
}
