package com.minhnb.finvera_be.market.provider.tcbs;

/**
 * Session/token lifecycle state for the TCBS provider.
 *
 * <p>The token is held only in runtime memory — never persisted, logged,
 * or surfaced in any API response or browser payload. Only the configured owner
 * may initiate renewal via the TcbsRenewalController OTP flow.
 */
public interface TcbsSessionState {

    /** Returns {@code true} if a non-expired bearer token is currently held. */
    boolean isTokenPresent();

    /**
     * Returns {@code true} if the last REST call succeeded (no connectivity
     * or server error). A missing token also implies unhealthy.
     */
    boolean isHealthy();

    /** Returns the current bearer token for use in Authorization headers. Never logs it. */
    String requireToken();
}
