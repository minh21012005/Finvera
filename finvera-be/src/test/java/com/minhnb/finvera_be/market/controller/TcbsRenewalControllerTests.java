package com.minhnb.finvera_be.market.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.controller.TcbsRenewalController.TokenRenewalRequest;
import com.minhnb.finvera_be.market.service.TcbsRenewalService;
import org.junit.jupiter.api.Test;

/**
 * The owner-only/CSRF guarantee is enforced globally by {@code OwnerSecurityConfiguration} and
 * not re-tested per controller. Renewal logic itself is covered by {@code TcbsRenewalServiceTests};
 * this only verifies the controller delegates the request fields as-is.
 */
class TcbsRenewalControllerTests {

    @Test
    void delegatesTheRequestFieldsToTheRenewalService() {
        TcbsRenewalService service = mock(TcbsRenewalService.class);
        TcbsRenewalController controller = new TcbsRenewalController(service);

        controller.renew(new TokenRenewalRequest("totp", "123456"));

        verify(service).renew("totp", "123456");
    }

    @Test
    void toleratesAMissingRequestBodyByPassingNulls() {
        TcbsRenewalService service = mock(TcbsRenewalService.class);
        TcbsRenewalController controller = new TcbsRenewalController(service);

        controller.renew(null);

        verify(service).renew(null, null);
    }

    @Test
    void mapsProviderHealthToTheStatusResponse() {
        TcbsRenewalService service = mock(TcbsRenewalService.class);
        when(service.status()).thenReturn(new TcbsRenewalService.Status("AUTH_REQUIRED", "PROVIDER_AUTH_REQUIRED"));
        TcbsRenewalController controller = new TcbsRenewalController(service);

        var response = controller.status();

        assertThat(response.state()).isEqualTo("AUTH_REQUIRED");
        assertThat(response.reasonCode()).isEqualTo("PROVIDER_AUTH_REQUIRED");
    }
}
