package com.minhnb.finvera_be.research.controller;

import com.minhnb.finvera_be.research.dto.IngestionCallbackRequest;
import com.minhnb.finvera_be.research.service.IngestionCallbackService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/ingestions")
public class InternalIngestionCallbackController {

    private final IngestionCallbackService callbackService;

    public InternalIngestionCallbackController(IngestionCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PatchMapping("/{researchItemId}/callback")
    public ResponseEntity<Void> reportIngestionResult(
            @PathVariable("researchItemId") UUID researchItemId,
            @RequestBody IngestionCallbackRequest request) {
        callbackService.handleCallback(researchItemId, request);
        return ResponseEntity.noContent().build();
    }
}
