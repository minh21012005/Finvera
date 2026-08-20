package com.minhnb.finvera_be.research.controller;

import com.minhnb.finvera_be.research.dto.RetrieveRequest;
import com.minhnb.finvera_be.research.dto.RetrieveResponse;
import com.minhnb.finvera_be.research.service.RetrievalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/research/retrieve")
public class ResearchRetrievalController {

    private final RetrievalService retrievalService;

    public ResearchRetrievalController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping
    public ResponseEntity<RetrieveResponse> retrievePassages(@RequestBody RetrieveRequest request) {
        RetrieveResponse response = retrievalService.retrievePassages(request);
        return ResponseEntity.ok(response);
    }
}
