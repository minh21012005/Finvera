package com.minhnb.finvera_be.stock.controller;

import com.minhnb.finvera_be.stock.dto.ScreenRequest;
import com.minhnb.finvera_be.stock.dto.ScreenResponse;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** `/api/v1/screener` per contracts/stock-screener.openapi.yaml. Owner-only, inherited from Feature 001/002. */
@RestController
@RequestMapping("/api/v1/screener")
public class ScreenerController {

    private final ScreenerService screenerService;

    public ScreenerController(ScreenerService screenerService) {
        this.screenerService = screenerService;
    }

    @PostMapping("/executions")
    ScreenResponse execute(@RequestBody(required = false) ScreenRequest request) {
        ScreenRequest effective = request == null
                ? new ScreenRequest(null, null, null, null, null, null, null, null)
                : request;
        var result = screenerService.execute(effective.toCriteria(), effective.effectiveSortField(),
                effective.effectiveSortDirection(), effective.effectiveLimit(), effective.effectiveOffset());
        return ScreenResponse.from(result, effective.effectiveLimit(), effective.effectiveOffset());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> handleInvalidFilterRange(IllegalArgumentException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "A selected filter's range is internally contradictory or malformed");
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Invalid filter range");
        problem.setProperty("reasonCode", "INVALID_FILTER_RANGE");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
