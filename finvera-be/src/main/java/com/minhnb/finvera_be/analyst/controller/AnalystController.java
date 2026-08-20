package com.minhnb.finvera_be.analyst.controller;

import com.minhnb.finvera_be.analyst.service.AnalystQueryService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analyst")
public class AnalystController {

    private final AnalystQueryService queryService;

    public AnalystController(AnalystQueryService queryService) {
        this.queryService = queryService;
    }
}
