package com.minhnb.finvera_be.stock.controller;

import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.dto.ScanRequest;
import com.minhnb.finvera_be.stock.dto.ScanResponse;
import com.minhnb.finvera_be.stock.service.strategy.StrategyScanService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** `/api/v1/strategies/{strategyCode}/scan` per contracts/strategy-signal.openapi.yaml. Owner-only. */
@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyScanController {

    private final StrategyScanService scanService;

    public StrategyScanController(StrategyScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping("/{strategyCode}/scan")
    ScanResponse scan(@PathVariable StrategyCode strategyCode, @RequestBody(required = false) ScanRequest request) {
        ScanRequest effective = request == null ? new ScanRequest(null, null) : request;
        var result = scanService.scan(strategyCode, effective.effectiveLimit(), effective.effectiveOffset());
        return ScanResponse.from(result);
    }
}
