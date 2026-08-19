package com.minhnb.finvera_be.stock.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * research R-009: a change detector derived from the ordered list of
 * contributing row ids and revisions behind one section response. It is
 * never a security token and grants nothing.
 */
public final class CoherenceKeys {

    private CoherenceKeys() {
    }

    public static String of(List<String> parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update((part == null ? "" : part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
            }
            String hex = HexFormat.of().formatHex(digest.digest());
            return hex.substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
