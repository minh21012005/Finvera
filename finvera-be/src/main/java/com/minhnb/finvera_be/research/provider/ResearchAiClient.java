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
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class ResearchAiClient {

    private static final Logger log = LoggerFactory.getLogger(ResearchAiClient.class);

    // Small, bounded retry for transient failures only (connection/timeout errors and 5xx
    // responses) — never for 4xx, which are not transient. This is safe to retry because every
    // call here targets finvera-ai's internal endpoints, which are keyed by research_item_id or
    // otherwise idempotent (a retry after a request that never arrived, or a response that was
    // lost in transit, does not create duplicate persisted state on the finvera-ai side).
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(300);

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

    private <T> T withRetry(String operation, Supplier<T> action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (HttpServerErrorException | ResourceAccessException e) {
                lastFailure = e;
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("Transient failure calling finvera-ai ({}), attempt {}/{}: {}",
                            operation, attempt, MAX_ATTEMPTS, e.getMessage());
                    sleepBeforeRetry();
                }
            }
        }
        throw lastFailure;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying finvera-ai call", ie);
        }
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

        // Deliberately NOT retried: each ChunkItem generates a fresh random vector_point_id
        // (chunking.py), so a retried submission that actually reached finvera-ai (e.g. the
        // response was lost after processing started) would kick off a second background job
        // for the same research_item_id with a disjoint chunk/vector set, risking orphaned
        // Qdrant points or a second terminal callback overwriting the first. The submission is
        // already protected by the (owner_id, idempotency_key) guard one layer up (research
        // R-013) — a genuine retry from the browser gets deduped there; a bare transport-level
        // retry of this specific call is not safe the way the other calls below are.
        return restClient.post()
                .uri("/ingestions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(IngestionAcceptedResponse.class);
    }

    public RetrieveChunksResponse retrieveChunks(RetrieveChunksRequest request) {
        return withRetry("retrieveChunks", () -> restClient.post()
                .uri("/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RetrieveChunksResponse.class));
    }

    public void deleteVectors(List<UUID> vectorPointIds) {
        if (vectorPointIds == null || vectorPointIds.isEmpty()) {
            return;
        }
        withRetry("deleteVectors", () -> restClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/vectors")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new DeleteVectorsRequest(vectorPointIds))
                .retrieve()
                .toBodilessEntity());
    }

    public InputStream streamSynthesize(AiInternalDto.SynthesizeRequest request) {
        // Retried like the other calls: exchange() only performs the request/response handshake here
        // (the returned InputStream isn't read yet), so a retry-on-transient-failure at this point
        // cannot duplicate any already-streamed bytes.
        return withRetry("streamSynthesize", () -> restClient.post()
                .uri("/synthesize")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(request)
                .exchange((req, res) -> res.getBody()));
    }
}
