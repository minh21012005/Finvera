package com.minhnb.finvera_be.market.domain.model;

public final class MarketTypes {

    private MarketTypes() {
    }

    public enum Venue {
        HOSE, HNX, UPCOM
    }

    public enum IndexCode {
        VN_INDEX, VN30, HNX_INDEX, UPCOM_INDEX
    }

    public enum SessionState {
        PRE_OPEN, OPEN, BREAK, INTERRUPTED, CLOSED, NON_TRADING_DAY, UNKNOWN;

        public boolean isActive() {
            return this == PRE_OPEN || this == OPEN || this == BREAK || this == INTERRUPTED;
        }
    }

    public enum DataStatus {
        CURRENT(0), DELAYED(1), STALE(2), PARTIAL(3), UNAVAILABLE(4);

        private final int severity;

        DataStatus(int severity) {
            this.severity = severity;
        }

        public static DataStatus mostActionable(DataStatus left, DataStatus right) {
            return left.severity >= right.severity ? left : right;
        }
    }

    public enum Direction {
        UP, DOWN, UNCHANGED
    }

    public enum AdjustmentStatus {
        RAW, PROVIDER_ADJUSTED, NOT_APPLICABLE, UNKNOWN
    }

    public enum RegimeLabel {
        BULL, EARLY_BULL, SIDEWAYS, EARLY_BEAR, BEAR
    }

    public enum FactorDirection {
        POSITIVE, NEGATIVE, NEUTRAL
    }
}
