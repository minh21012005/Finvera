package com.minhnb.finvera_be.analyst.provider;

import com.minhnb.finvera_be.research.config.ResearchProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AnalystAiClient {

    private final RestClient restClient;
    private final ResearchProperties researchProperties;

    public AnalystAiClient(RestClient.Builder restClientBuilder, ResearchProperties researchProperties) {
        this.researchProperties = researchProperties;
        this.restClient = restClientBuilder
                .baseUrl(researchProperties.aiServiceUrl())
                .defaultHeader("X-Internal-Api-Key", researchProperties.internalApiKey())
                .build();
    }
}
