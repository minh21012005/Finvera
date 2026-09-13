package com.minhnb.finvera_be.positioning.controller;

import com.minhnb.finvera_be.positioning.dto.PositionSizingDtos.SizingRequest;
import com.minhnb.finvera_be.positioning.dto.PositionSizingDtos.SizingResult;
import com.minhnb.finvera_be.positioning.service.PositionSizingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/position-sizing")
public class PositionSizingController {
    private final PositionSizingService service;
    public PositionSizingController(PositionSizingService service) { this.service = service; }
    @PostMapping("/calculate")
    public SizingResult calculate(@Valid @RequestBody SizingRequest request) { return service.calculate(request); }
}
