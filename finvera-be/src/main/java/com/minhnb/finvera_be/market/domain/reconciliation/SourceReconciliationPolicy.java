package com.minhnb.finvera_be.market.domain.reconciliation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact, versioned policy for overlapping TCBS and Vnstock raw observations. */
public final class SourceReconciliationPolicy {
    public static final String VERSION = "tcbs-vnstock-reconciliation-v1";

    public Decision reconcile(Fact tcbs, Fact vnstock) {
        if (tcbs == null && vnstock == null) return Decision.UNAVAILABLE;
        if (tcbs == null) return Decision.VNSTOCK_HISTORICAL_BOOTSTRAP;
        if (vnstock == null) return Decision.TCBS_CANONICAL;
        if (tcbs.adjustmentStatus() != AdjustmentStatus.RAW || vnstock.adjustmentStatus() != AdjustmentStatus.RAW)
            return Decision.NON_COMPARABLE;
        return exact(tcbs.close(), vnstock.close()) && exact(tcbs.reference(), vnstock.reference())
                ? Decision.TCBS_CANONICAL : Decision.SOURCE_CONFLICT;
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
