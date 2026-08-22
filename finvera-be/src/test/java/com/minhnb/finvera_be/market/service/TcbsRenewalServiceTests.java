package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.provider.MarketDataProvider;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealth;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderHealthState;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsHttpSessionState;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice.ProviderAuthRequiredException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TcbsRenewalServiceTests {

    @Test
    void reportsProviderAuthRequiredWhenNoLiveSessionBeanExists() {
        TcbsRenewalService service = new TcbsRenewalService(Optional.empty(), Optional.of(mock(MarketDataProvider.class)));

        assertThatThrownBy(() -> service.renew("totp", "123456"))
                .isInstanceOf(ProviderAuthRequiredException.class);
    }

    @Test
    void rejectsAnUnknownOtpMethodWithoutCallingTheSession() {
        TcbsHttpSessionState sessionState = mock(TcbsHttpSessionState.class);
        TcbsRenewalService service = new TcbsRenewalService(Optional.of(sessionState), Optional.of(mock(MarketDataProvider.class)));

        assertThatThrownBy(() -> service.renew("sms-only", "123456"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sessionState, never()).renewWithTotp(any());
        verify(sessionState, never()).renewWithEmailSms(any());
    }

    @Test
    void rejectsAMissingOtpMethodWithoutCallingTheSession() {
        TcbsHttpSessionState sessionState = mock(TcbsHttpSessionState.class);
        TcbsRenewalService service = new TcbsRenewalService(Optional.of(sessionState), Optional.of(mock(MarketDataProvider.class)));

        assertThatThrownBy(() -> service.renew(null, "123456")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dispatchesTotpRenewalToTheSessionState() {
        TcbsHttpSessionState sessionState = mock(TcbsHttpSessionState.class);
        TcbsRenewalService service = new TcbsRenewalService(Optional.of(sessionState), Optional.of(mock(MarketDataProvider.class)));

        service.renew("totp", "123456");

        verify(sessionState).renewWithTotp("123456");
        verify(sessionState, never()).renewWithEmailSms(any());
    }

    @Test
    void dispatchesEmailSmsRenewalToTheSessionState() {
        TcbsHttpSessionState sessionState = mock(TcbsHttpSessionState.class);
        TcbsRenewalService service = new TcbsRenewalService(Optional.of(sessionState), Optional.of(mock(MarketDataProvider.class)));

        service.renew("email-sms", "654321");

        verify(sessionState).renewWithEmailSms("654321");
        verify(sessionState, never()).renewWithTotp(any());
    }

    @Test
    void statusDelegatesToTheProviderHealthCheck() {
        MarketDataProvider provider = mock(MarketDataProvider.class);
        when(provider.health()).thenReturn(new ProviderHealth(ProviderHealthState.AUTH_REQUIRED, "PROVIDER_AUTH_REQUIRED"));
        TcbsRenewalService service = new TcbsRenewalService(Optional.empty(), Optional.of(provider));

        TcbsRenewalService.Status status = service.status();

        assertThat(status.state()).isEqualTo("AUTH_REQUIRED");
        assertThat(status.reasonCode()).isEqualTo("PROVIDER_AUTH_REQUIRED");
    }

    @Test
    void statusReportsReadyWhenNoMarketDataProviderBeanExists() {
        TcbsRenewalService service = new TcbsRenewalService(Optional.empty(), Optional.empty());

        TcbsRenewalService.Status status = service.status();

        assertThat(status.state()).isEqualTo("READY");
        assertThat(status.reasonCode()).isEqualTo("LIVE_MODE_DISABLED");
    }
}
