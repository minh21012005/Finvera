package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.minhnb.finvera_be.market.provider.tcbs.TcbsHttpSessionState;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice.ProviderAuthRequiredException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TcbsRenewalServiceTests {

    @Test
    void reportsProviderAuthRequiredWhenNoLiveSessionBeanExists() {
        TcbsRenewalService service = new TcbsRenewalService(Optional.empty());

        assertThatThrownBy(() -> service.renew("totp", "123456"))
                .isInstanceOf(ProviderAuthRequiredException.class);
    }

    @Test
    void rejectsAnUnknownOtpMethodWithoutCallingTheSession() {
        TcbsHttpSessionState sessionState = mock(TcbsHttpSessionState.class);
        TcbsRenewalService service = new TcbsRenewalService(Optional.of(sessionState));

        assertThatThrownBy(() -> service.renew("sms-only", "123456"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sessionState, never()).renewWithTotp(any());
        verify(sessionState, never()).renewWithEmailSms(any());
    }

    @Test
    void rejectsAMissingOtpMethodWithoutCallingTheSession() {
        TcbsHttpSessionState sessionState = mock(TcbsHttpSessionState.class);
        TcbsRenewalService service = new TcbsRenewalService(Optional.of(sessionState));

        assertThatThrownBy(() -> service.renew(null, "123456")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dispatchesTotpRenewalToTheSessionState() {
        TcbsHttpSessionState sessionState = mock(TcbsHttpSessionState.class);
        TcbsRenewalService service = new TcbsRenewalService(Optional.of(sessionState));

        service.renew("totp", "123456");

        verify(sessionState).renewWithTotp("123456");
        verify(sessionState, never()).renewWithEmailSms(any());
    }

    @Test
    void dispatchesEmailSmsRenewalToTheSessionState() {
        TcbsHttpSessionState sessionState = mock(TcbsHttpSessionState.class);
        TcbsRenewalService service = new TcbsRenewalService(Optional.of(sessionState));

        service.renew("email-sms", "654321");

        verify(sessionState).renewWithEmailSms("654321");
        verify(sessionState, never()).renewWithTotp(any());
    }
}
