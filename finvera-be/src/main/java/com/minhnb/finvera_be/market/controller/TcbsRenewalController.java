package com.minhnb.finvera_be.market.controller;

import com.minhnb.finvera_be.market.service.TcbsRenewalService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner-only TCBS live-session renewal. Reachable only by the authenticated owner: every request
 * already passes through {@code OwnerSecurityConfiguration}'s filter chain (session auth + CSRF)
 * before it reaches this controller. The owner's TCBS API key stays server-side
 * ({@code TcbsProviderProperties}); only the OTP the owner types crosses this boundary, and it is
 * never logged or echoed back. Renewal logic itself lives in {@link TcbsRenewalService}.
 */
@RestController
@RequestMapping("/api/v1/market/providers/tcbs")
public class TcbsRenewalController {

    private final TcbsRenewalService renewalService;

    public TcbsRenewalController(TcbsRenewalService renewalService) {
        this.renewalService = renewalService;
    }

    @PostMapping("/token-renewal")
    void renew(@RequestBody TokenRenewalRequest request) {
        renewalService.renew(request == null ? null : request.otpMethod(), request == null ? null : request.otp());
    }

    public record TokenRenewalRequest(String otpMethod, String otp) {
    }
}
