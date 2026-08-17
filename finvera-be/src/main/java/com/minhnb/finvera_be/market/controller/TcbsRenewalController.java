package com.minhnb.finvera_be.market.controller;

import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice.ProviderAuthRequiredException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market/providers/tcbs")
public class TcbsRenewalController {

    @PostMapping("/token-renewal")
    void renewWhileProviderGateIsOpen() {
        throw new ProviderAuthRequiredException();
    }
}
