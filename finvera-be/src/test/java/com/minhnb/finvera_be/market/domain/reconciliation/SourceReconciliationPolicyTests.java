package com.minhnb.finvera_be.market.domain.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SourceReconciliationPolicyTests {
    private final SourceReconciliationPolicy policy = new SourceReconciliationPolicy();

    @Test void matchingRawFactsPreferTcbs() {
        var fact = raw("101.500000", "100.000000");
        assertThat(policy.reconcile(fact, fact)).isEqualTo(SourceReconciliationPolicy.Decision.TCBS_CANONICAL);
    }
    @Test void oneSixthDecimalDifferenceIsConflict() {
        assertThat(policy.reconcile(raw("101.500000", "100.000000"), raw("101.500001", "100.000000")))
                .isEqualTo(SourceReconciliationPolicy.Decision.SOURCE_CONFLICT);
    }
    @Test void adjustedFactsAreNotComparedAndVnstockAloneIsBootstrap() {
        var adjusted = new SourceReconciliationPolicy.Fact(new BigDecimal("101.5"), new BigDecimal("100"),
                SourceReconciliationPolicy.AdjustmentStatus.PROVIDER_ADJUSTED);
        assertThat(policy.reconcile(raw("101.500000", "100.000000"), adjusted))
                .isEqualTo(SourceReconciliationPolicy.Decision.NON_COMPARABLE);
        assertThat(policy.reconcile(null, raw("101.500000", "100.000000")))
                .isEqualTo(SourceReconciliationPolicy.Decision.VNSTOCK_HISTORICAL_BOOTSTRAP);
    }
    private static SourceReconciliationPolicy.Fact raw(String close, String reference) {
        return new SourceReconciliationPolicy.Fact(new BigDecimal(close), new BigDecimal(reference),
                SourceReconciliationPolicy.AdjustmentStatus.RAW);
    }
}
