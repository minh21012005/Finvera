package com.minhnb.finvera_be.analyst.service;

import com.minhnb.finvera_be.analyst.repository.AnalystQueryRepository;
import com.minhnb.finvera_be.analyst.repository.AnalystToolCallRepository;
import org.springframework.stereotype.Service;

@Service
public class AnalystQueryService {

    private final AnalystQueryRepository queryRepository;
    private final AnalystToolCallRepository toolCallRepository;

    public AnalystQueryService(
            AnalystQueryRepository queryRepository,
            AnalystToolCallRepository toolCallRepository) {
        this.queryRepository = queryRepository;
        this.toolCallRepository = toolCallRepository;
    }
}
