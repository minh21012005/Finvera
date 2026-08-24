package com.minhnb.finvera_be.market.controller;

import com.minhnb.finvera_be.market.service.TcbsRenewalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/market/providers/tcbs")
public class TcbsRenewalController {
    private final TcbsRenewalService service;
    public TcbsRenewalController(TcbsRenewalService service) { this.service = service; }
    @GetMapping("/status") StatusResponse status() {
        var status = service.status();
        return new StatusResponse(status.state(), status.reasonCode());
    }
    @PostMapping("/token-renewal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void renew(@RequestBody RenewalRequest request) {
        service.renew(request == null ? null : request.otp());
    }
    public record RenewalRequest(String otp) { }
    public record StatusResponse(String state, String reasonCode) { }
}
