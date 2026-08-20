package com.minhnb.finvera_be.research.provider;

import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.research.provider.AiInternalDto.DeleteVectorsRequest;
import com.minhnb.finvera_be.research.provider.AiInternalDto.IngestionAcceptedResponse;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RetrieveChunksRequest;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RetrieveChunksResponse;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class ResearchAiClient {

    private final RestClient restClient;
    private final ResearchProperties properties;

    public ResearchAiClient(ResearchProperties properties) {
        this.properties = properties;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .baseUrl(properties.aiServiceUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    public IngestionAcceptedResponse submitIngestion(
            UUID researchItemId,
            String itemType,
            UUID ownerId,
            byte[] content,
            String filename,
            String text,
            String mimeType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("researchItemId", researchItemId.toString());
        body.add("itemType", itemType);
        body.add("ownerId", ownerId.toString());

        if (content != null && content.length > 0) {
            String name = (filename != null && !filename.isBlank()) ? filename : "document.pdf";
            body.add("content", new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return name;
                }
            });
        }
        if (text != null && !text.isBlank()) {
            body.add("text", text);
        }
        if (mimeType != null && !mimeType.isBlank()) {
            body.add("mimeType", mimeType);
        }

        return restClient.post()
                .uri("/ingestions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(IngestionAcceptedResponse.class);
    }

    public RetrieveChunksResponse retrieveChunks(RetrieveChunksRequest request) {
        return restClient.post()
                .uri("/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RetrieveChunksResponse.class);
    }

    public void deleteVectors(List<UUID> vectorPointIds) {
        if (vectorPointIds == null || vectorPointIds.isEmpty()) {
            return;
        }
        restClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/vectors")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new DeleteVectorsRequest(vectorPointIds))
                .retrieve()
                .toBodilessEntity();
    }

    public InputStream streamSynthesize(AiInternalDto.SynthesizeRequest request) {
        return restClient.post()
                .uri("/synthesize")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(request)
                .exchange((req, res) -> res.getBody());
    }
}
