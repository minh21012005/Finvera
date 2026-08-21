package com.minhnb.finvera_be.market.service;

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
 */
@Service
public class TcbsRenewalService {

    private final Optional<TcbsHttpSessionState> sessionState;

    public TcbsRenewalService(Optional<TcbsHttpSessionState> sessionState) {
        this.sessionState = sessionState;
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
