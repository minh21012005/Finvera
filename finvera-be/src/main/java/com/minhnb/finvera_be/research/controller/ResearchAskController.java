package com.minhnb.finvera_be.research.controller;

import com.minhnb.finvera_be.research.dto.AskRequest;
import com.minhnb.finvera_be.research.service.AskService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/research/ask")
public class ResearchAskController {

    private final AskService askService;

    public ResearchAskController(AskService askService) {
        this.askService = askService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askQuestion(@Valid @RequestBody AskRequest request) {
        return askService.askQuestion(request);
    }
}
