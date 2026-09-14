package com.minhnb.finvera_be.backtest.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/** Quy tắc thuần xác định một tín hiệu có được mở thêm tranche hay không. */
public final class PyramidingV1 {
    public static final String RULE_VERSION = "pyramiding-v1";
    public static final int MAX_OPEN_TRANCHES = 4;
    public static final BigDecimal STEP_ATR = new BigDecimal("0.5");
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);

    private PyramidingV1() { }

    public record Decision(boolean accepted, String reasonCode) { }

    public static Decision evaluate(boolean priorSignal, boolean currentSignal,
            List<BigDecimal> openEffectiveEntries, BigDecimal candidateEffectiveEntry, BigDecimal signalAtr14) {
        if (!currentSignal || priorSignal) return new Decision(false, "NOT_NEW_SIGNAL_EPISODE");
        if (openEffectiveEntries == null || openEffectiveEntries.isEmpty()) return new Decision(true, null);
        if (openEffectiveEntries.size() >= MAX_OPEN_TRANCHES) return new Decision(false, "MAX_TRANCHES_REACHED");
        if (candidateEffectiveEntry == null || signalAtr14 == null || signalAtr14.signum() <= 0) {
            return new Decision(false, "INVALID_LEVELS");
        }
        BigDecimal latest = openEffectiveEntries.get(openEffectiveEntries.size() - 1);
        BigDecimal threshold = latest.add(signalAtr14.multiply(STEP_ATR, MC), MC);
        return candidateEffectiveEntry.compareTo(threshold) >= 0
                ? new Decision(true, null) : new Decision(false, "PYRAMID_PRICE_STEP_NOT_MET");
    }
}
