package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealth;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderAuthenticationRequiredException;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsHttpSessionState;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsThesisWebSocketClient;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice.ProviderAuthRequiredException;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Owner-only boundary for transient TCBS OTP renewal and live-stream health. */
@Service
public class TcbsRenewalService {
    private final Optional<TcbsHttpSessionState> session;
    private final Optional<TcbsThesisWebSocketClient> client;

    public TcbsRenewalService(Optional<TcbsHttpSessionState> session,
            Optional<TcbsThesisWebSocketClient> client) {
        this.session = session;
        this.client = client;
    }

    public Status status() {
        return client.map(TcbsThesisWebSocketClient::health).map(this::status)
                .orElse(new Status("READY", "LIVE_OVERLAY_DISABLED"));
    }

    public void renew(String otp) {
        TcbsHttpSessionState state = session.orElseThrow(ProviderAuthRequiredException::new);
        try {
            state.renewWithTotp(otp);
            client.orElseThrow(ProviderAuthRequiredException::new).connect();
        } catch (ProviderAuthenticationRequiredException | IllegalArgumentException exception) {
            throw new ProviderAuthRequiredException();
        }
    }

    private Status status(ProviderHealth health) { return new Status(health.state().name(), health.reasonCode()); }
    public record Status(String state, String reasonCode) { }
}
