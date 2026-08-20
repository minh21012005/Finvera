package com.minhnb.finvera_be.analyst.provider;

import com.minhnb.finvera_be.analyst.config.AnalystProperties;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.InternalAskRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AnalystAiClient {

    private static final Logger log = LoggerFactory.getLogger(AnalystAiClient.class);

    private final RestClient restClient;

    public AnalystAiClient(AnalystProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl(properties.aiServiceUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    public void streamAsk(InternalAskRequest request, Consumer<String> sseLineConsumer) {
        restClient.post()
                .uri("/internal/v1/analyst/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, res) -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(res.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sseLineConsumer.accept(line);
                        }
                    } catch (Exception e) {
                        log.error("Error reading SSE stream from AI service", e);
                        throw new RuntimeException("AI service stream reading failed", e);
                    }
                    return null;
                });
    }
}
