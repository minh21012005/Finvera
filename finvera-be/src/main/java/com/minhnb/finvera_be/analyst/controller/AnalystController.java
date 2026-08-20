package com.minhnb.finvera_be.analyst.controller;

import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.AskAnalystRequest;
import com.minhnb.finvera_be.analyst.service.AnalystService;
import com.minhnb.finvera_be.portfolio.service.OwnerScopedAccess;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/analyst")
public class AnalystController {

    private final AnalystService analystService;
    private final OwnerScopedAccess ownerScopedAccess;

    public AnalystController(AnalystService analystService, OwnerScopedAccess ownerScopedAccess) {
        this.analystService = analystService;
        this.ownerScopedAccess = ownerScopedAccess;
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody AskAnalystRequest request) {
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();
        return analystService.askStream(ownerId, request);
    }
}
