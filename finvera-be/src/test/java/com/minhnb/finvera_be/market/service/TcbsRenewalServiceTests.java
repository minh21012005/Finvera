package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderAuthenticationRequiredException;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealth;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealthState;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsHttpSessionState;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsThesisWebSocketClient;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice.ProviderAuthRequiredException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TcbsRenewalServiceTests {
    @Mock TcbsHttpSessionState session;
    @Mock TcbsThesisWebSocketClient client;

    @Test
    void renewsTokenBeforeConnectingStream() {
        var service = new TcbsRenewalService(Optional.of(session), Optional.of(client));
        service.renew("123456");
        var order = org.mockito.Mockito.inOrder(session, client);
        order.verify(session).renewWithTotp("123456");
        order.verify(client).connect();
    }

    @Test
    void translatesProviderAuthenticationFailureToSafeApiException() {
        var service = new TcbsRenewalService(Optional.of(session), Optional.of(client));
        org.mockito.Mockito.doThrow(new ProviderAuthenticationRequiredException())
                .when(session).renewWithTotp("expired");
        assertThatThrownBy(() -> service.renew("expired"))
                .isInstanceOf(ProviderAuthRequiredException.class);
    }

    @Test
    void reportsDisabledAndReadyWithoutExposingTokenDetails() {
        assertThat(new TcbsRenewalService(Optional.empty(), Optional.empty()).status())
                .isEqualTo(new TcbsRenewalService.Status("READY", "LIVE_OVERLAY_DISABLED"));
        when(client.health()).thenReturn(new ProviderHealth(ProviderHealthState.READY, "READY"));
        assertThat(new TcbsRenewalService(Optional.of(session), Optional.of(client)).status())
                .isEqualTo(new TcbsRenewalService.Status("READY", "READY"));
        verify(client).health();
    }
}
