package com.minhnb.finvera_be.research.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.minhnb.finvera_be.research.domain.ResearchItemType;
import com.minhnb.finvera_be.research.dto.AskCitation;
import com.minhnb.finvera_be.research.dto.AskFinalResult;
import com.minhnb.finvera_be.research.dto.AskRequest;
import com.minhnb.finvera_be.research.dto.AskStreamEvent;
import com.minhnb.finvera_be.research.dto.SourceType;
import com.minhnb.finvera_be.research.entity.NewsArticleEntity;
import com.minhnb.finvera_be.research.entity.ResearchChunkEntity;
import com.minhnb.finvera_be.research.entity.ResearchDocumentEntity;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RankedChunkDto;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RetrieveChunksRequest;
import com.minhnb.finvera_be.research.provider.AiInternalDto.RetrieveChunksResponse;
import com.minhnb.finvera_be.research.provider.AiInternalDto.SynthesizePassageDto;
import com.minhnb.finvera_be.research.provider.AiInternalDto.SynthesizeRequest;
import com.minhnb.finvera_be.research.provider.ResearchAiClient;
import com.minhnb.finvera_be.research.repository.NewsArticleRepository;
import com.minhnb.finvera_be.research.repository.ResearchChunkRepository;
import com.minhnb.finvera_be.research.repository.ResearchDocumentRepository;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);
    private static final String DEFAULT_REFUSAL = "Không tìm thấy thông tin hoặc đoạn trích phù hợp trong kho tài liệu của bạn.";

    private final ResearchAiClient aiClient;
    private final ResearchChunkRepository chunkRepository;
    private final ResearchDocumentRepository documentRepository;
    private final NewsArticleRepository newsRepository;
    private final OwnerScopedResearchAccess ownerAccess;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public AskService(
            ResearchAiClient aiClient,
            ResearchChunkRepository chunkRepository,
            ResearchDocumentRepository documentRepository,
            NewsArticleRepository newsRepository,
            OwnerScopedResearchAccess ownerAccess,
            ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.newsRepository = newsRepository;
        this.ownerAccess = ownerAccess;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SseEmitter askQuestion(AskRequest request) {
        if (request.query() == null || request.query().trim().isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }

        UUID ownerId = ownerAccess.getAuthenticatedOwnerId();
        SseEmitter emitter = new SseEmitter(60_000L); // 60 second timeout

        executorService.submit(() -> {
            try {
                processStreamingAsk(request, ownerId, emitter);
            } catch (Exception e) {
                log.error("Unhandled error during streamed ask for owner {}: {}", ownerId, e.getMessage(), e);
                try {
                    AskFinalResult fallback = new AskFinalResult(DEFAULT_REFUSAL, Collections.emptyList(), true);
                    emitter.send(AskStreamEvent.finalResult(fallback));
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        });

        return emitter;
    }

    private void processStreamingAsk(AskRequest request, UUID ownerId, SseEmitter emitter) throws Exception {
        String symbol = null;
        String documentType = null;
        String newsCategory = null;
        String dateFrom = null;
        String dateTo = null;

        if (request.filters() != null) {
            var f = request.filters();
            if (f.symbol() != null && !f.symbol().isBlank()) {
                symbol = f.symbol().trim().toUpperCase();
            }
            if (f.documentType() != null) {
                documentType = f.documentType().name();
            }
            if (f.newsCategory() != null) {
                newsCategory = f.newsCategory().name();
            }
            if (f.dateFrom() != null) {
                dateFrom = f.dateFrom().toString();
            }
            if (f.dateTo() != null) {
                dateTo = f.dateTo().toString();
            }
        }

        RetrieveChunksResponse aiResponse;
        try {
            aiResponse = aiClient.retrieveChunks(new RetrieveChunksRequest(
                    request.query().trim(),
                    ownerId,
                    symbol,
                    documentType,
                    newsCategory,
                    dateFrom,
                    dateTo,
                    8));
        } catch (Exception e) {
            log.error("AI retrieve failed for ask query: {}", e.getMessage());
            AskFinalResult fallback = new AskFinalResult(DEFAULT_REFUSAL, Collections.emptyList(), true);
            emitter.send(AskStreamEvent.finalResult(fallback));
            emitter.complete();
            return;
        }

        if (aiResponse == null || aiResponse.chunks() == null || aiResponse.chunks().isEmpty()) {
            AskFinalResult refusal = new AskFinalResult(DEFAULT_REFUSAL, Collections.emptyList(), true);
            emitter.send(AskStreamEvent.finalResult(refusal));
            emitter.complete();
            return;
        }

        List<RankedChunkDto> rankedChunks = aiResponse.chunks();
        List<UUID> chunkIds = rankedChunks.stream().map(RankedChunkDto::chunkId).toList();

        // Authoritative resolution from PostgreSQL (F4)
        List<ResearchChunkEntity> chunkEntities = chunkRepository.findByIdInAndOwnerId(chunkIds, ownerId);
        Map<UUID, ResearchChunkEntity> chunkMap = new HashMap<>();
        for (var c : chunkEntities) {
            chunkMap.put(c.getId(), c);
        }

        List<SynthesizePassageDto> passages = new ArrayList<>();
        for (var ranked : rankedChunks) {
            ResearchChunkEntity c = chunkMap.get(ranked.chunkId());
            if (c != null && c.getContentText() != null && !c.getContentText().isBlank()) {
                passages.add(new SynthesizePassageDto(c.getId(), c.getContentText()));
            }
        }

        if (passages.isEmpty()) {
            AskFinalResult refusal = new AskFinalResult(DEFAULT_REFUSAL, Collections.emptyList(), true);
            emitter.send(AskStreamEvent.finalResult(refusal));
            emitter.complete();
            return;
        }

        // Call synthesis streaming endpoint
        SynthesizeRequest synthReq = new SynthesizeRequest(ownerId, request.query().trim(), passages);
        InputStream inputStream = aiClient.streamSynthesize(synthReq);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("data:")) {
                    continue;
                }

                String jsonStr = line.substring(5).trim();
                if (jsonStr.isEmpty()) {
                    continue;
                }

                JsonNode node = objectMapper.readTree(jsonStr);
                String type = node.path("type").asText();

                if ("delta".equalsIgnoreCase(type)) {
                    String delta = node.path("textDelta").asText();
                    emitter.send(AskStreamEvent.delta(delta));
                } else if ("final".equalsIgnoreCase(type)) {
                    JsonNode finalNode = node.path("final");
                    String answer = finalNode.path("answer").asText();
                    boolean refused = finalNode.path("refused").asBoolean(false);

                    List<AskCitation> publicCitations = new ArrayList<>();
                    if (!refused && finalNode.has("citations") && finalNode.path("citations").isArray()) {
                        for (JsonNode citNode : finalNode.path("citations")) {
                            String chunkIdStr = citNode.path("chunkId").asText();
                            String claimText = citNode.path("claimText").asText();
                            if (chunkIdStr != null && !chunkIdStr.isBlank()) {
                                UUID chunkId = UUID.fromString(chunkIdStr);
                                ResearchChunkEntity chunk = chunkMap.get(chunkId);
                                if (chunk == null) {
                                    chunk = chunkRepository.findByIdAndOwnerId(chunkId, ownerId).orElse(null);
                                }

                                if (chunk != null) {
                                    if (chunk.getItemType() == ResearchItemType.DOCUMENT && chunk.getResearchDocumentId() != null) {
                                        var doc = documentRepository.findByIdAndOwnerId(chunk.getResearchDocumentId(), ownerId).orElse(null);
                                        if (doc != null) {
                                            String location = "Page " + chunk.getPageNumber();
                                            publicCitations.add(new AskCitation(
                                                    claimText,
                                                    SourceType.DOCUMENT,
                                                    doc.getId(),
                                                    doc.getTitle(),
                                                    location,
                                                    doc.getSource()));
                                        }
                                    } else if (chunk.getItemType() == ResearchItemType.NEWS_ARTICLE && chunk.getNewsArticleId() != null) {
                                        var news = newsRepository.findByIdAndOwnerId(chunk.getNewsArticleId(), ownerId).orElse(null);
                                        if (news != null) {
                                            int pIndex = chunk.getParagraphIndex() != null ? chunk.getParagraphIndex() + 1 : 1;
                                            String location = "Paragraph " + pIndex;
                                            publicCitations.add(new AskCitation(
                                                    claimText,
                                                    SourceType.NEWS_ARTICLE,
                                                    news.getId(),
                                                    news.getTitle(),
                                                    location,
                                                    news.getSource()));
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AskFinalResult finalResult = new AskFinalResult(answer, publicCitations, refused);
                    emitter.send(AskStreamEvent.finalResult(finalResult));
                    emitter.complete();
                    return;
                }
            }
        }

        // If stream ended without final event
        emitter.complete();
    }
}
