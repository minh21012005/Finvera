package com.minhnb.finvera_be.stock.domain.technical;

import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.TechnicalBar;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

/**
 * Full-length (64 hex character) SHA-256 {@code input_set_hash} per the
 * result contract in {@code contracts/technical-indicators-v1.md}: "SHA-256
 * over the canonical ordered (trading_date, value) list of consumed bars."
 * The contract does not pin down which per-bar field is "the value" for
 * every indicator, so this hashes the actual field(s) that indicator's
 * formula consumes — close for price-derived indicators, the high/low/close
 * triple for ATR14 (its true-range formula needs all three), and volume for
 * the two volume indicators — so a replay (SC-003) can only agree with the
 * stored decimal if it used the same consumed values.
 */
final class TechnicalInputHashes {

    private TechnicalInputHashes() {
    }

    static String ofCloses(List<TechnicalBar> bars) {
        return hash(bars, bar -> bar.tradingDate() + "|" + plain(bar.close()));
    }

    static String ofHighLowClose(List<TechnicalBar> bars) {
        return hash(bars, bar -> bar.tradingDate() + "|" + plain(bar.high()) + "|" + plain(bar.low()) + "|"
                + plain(bar.close()));
    }

    static String ofVolumes(List<TechnicalBar> bars) {
        return hash(bars, bar -> bar.tradingDate() + "|" + bar.volume());
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String hash(List<TechnicalBar> bars, Function<TechnicalBar, String> canonicalLine) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (TechnicalBar bar : bars) {
                digest.update(canonicalLine.apply(bar).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
