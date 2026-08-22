package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.provider.MarketDataProvider;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealth;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsHttpSessionState;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice.ProviderAuthRequiredException;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Owner-only TCBS live-session renewal, invoked only by {@code TcbsRenewalController}. Kept as a
 * service (rather than the controller depending on {@code TcbsHttpSessionState} directly) so
 * controllers stay in the controller-&gt;service layering enforced by
 * {@code LayeredArchitectureTests}.
 *
 * <p>Outside live mode (or before any owner has ever renewed) no {@link TcbsHttpSessionState}
 * bean exists, so renewal always reports {@code PROVIDER_AUTH_REQUIRED} rather than a 404/500.
 *
 * <p>{@code MarketDataProvider} is likewise {@link Optional}: no bean of that type exists at all
 * in fixture mode (only the live TCBS wiring in {@code MarketConfiguration} registers one), so a
 * required dependency here would break application startup outside live mode.
 */
@Service
public class TcbsRenewalService {

    private static final Status LIVE_MODE_DISABLED = new Status("READY", "LIVE_MODE_DISABLED");

    private final Optional<TcbsHttpSessionState> sessionState;
    private final Optional<MarketDataProvider> provider;

    public TcbsRenewalService(Optional<TcbsHttpSessionState> sessionState, Optional<MarketDataProvider> provider) {
        this.sessionState = sessionState;
        this.provider = provider;
    }

    /**
     * Lets the owner-facing UI show a "needs re-authentication" banner without polling logs.
     * Returns a service-owned type (not {@link ProviderHealth} directly) so the controller never
     * needs to import the {@code provider} package ({@code LayeredArchitectureTests}).
     */
    public Status status() {
        return provider.map(p -> {
            ProviderHealth health = p.health();
            return new Status(health.state().name(), health.reasonCode());
        }).orElse(LIVE_MODE_DISABLED);
    }

    public record Status(String state, String reasonCode) {
    }

    public void renew(String otpMethod, String otp) {
        TcbsHttpSessionState state = sessionState.orElseThrow(ProviderAuthRequiredException::new);
        if (otpMethod == null) {
            throw new IllegalArgumentException("otpMethod is required");
        }
        switch (otpMethod) {
            case "totp" -> state.renewWithTotp(otp);
            case "email-sms" -> state.renewWithEmailSms(otp);
            default -> throw new IllegalArgumentException("otpMethod must be 'totp' or 'email-sms'");
        }
    }
}
