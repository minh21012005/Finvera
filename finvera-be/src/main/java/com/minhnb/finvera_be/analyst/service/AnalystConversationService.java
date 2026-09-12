package com.minhnb.finvera_be.analyst.service;

import static com.minhnb.finvera_be.analyst.dto.AnalystConversationDtos.*;

import com.minhnb.finvera_be.analyst.config.AnalystProperties;
import com.minhnb.finvera_be.analyst.domain.ConversationContextWindowPolicy;
import com.minhnb.finvera_be.analyst.domain.ConversationExchangeStatus;
import com.minhnb.finvera_be.analyst.domain.ConversationTitlePolicy;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.AskAnalystRequest;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.PriorTurnDto;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.PublicFinalEventDto;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.PublicStructuredClaimDto;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.PublicDocumentClaimDto;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.ToolCallEventDto;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.ClaimCoverage;
import com.minhnb.finvera_be.analyst.entity.AnalystConversationEntity;
import com.minhnb.finvera_be.analyst.entity.AnalystConversationExchangeEntity;
import com.minhnb.finvera_be.analyst.repository.AnalystConversationExchangeRepository;
import com.minhnb.finvera_be.analyst.repository.AnalystConversationRepository;
import com.minhnb.finvera_be.analyst.service.AnalystConversationExceptions.ConversationConflictException;
import com.minhnb.finvera_be.analyst.service.AnalystConversationExceptions.ConversationNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

@Service
public class AnalystConversationService {
    private static final Logger log = LoggerFactory.getLogger(AnalystConversationService.class);
    private static final long SSE_TIMEOUT_MS = 300_000L;
    private final AnalystConversationRepository conversations;
    private final AnalystConversationExchangeRepository exchanges;
    private final AnalystService analystService;
    private final AnalystProperties properties;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meters;

    public AnalystConversationService(AnalystConversationRepository conversations,
            AnalystConversationExchangeRepository exchanges, AnalystService analystService,
            AnalystProperties properties, TransactionTemplate transactions, ObjectMapper objectMapper,
            Clock clock, MeterRegistry meters) {
        this.conversations=conversations; this.exchanges=exchanges; this.analystService=analystService;
        this.properties=properties; this.transactions=transactions; this.objectMapper=objectMapper;
        this.clock=clock; this.meters=meters;
    }

    public SseEmitter ask(UUID ownerId, ConversationAskRequest request) {
        String question = normalizeQuestion(request.question());
        String symbol = normalizeSymbol(request.symbol());
        PreparedAsk prepared;
        try {
            prepared = transactions.execute(status -> prepare(ownerId, request, question, symbol));
        } catch (ConversationConflictException collision) {
            recordRequestConflict(collision.reasonCode());
            throw collision;
        } catch (DataIntegrityViolationException collision) {
            if (causedByConstraint(collision, "uq_analyst_conversation_exchange_request")) {
                recordRequestConflict("REQUEST_IN_PROGRESS");
                throw new ConversationConflictException("REQUEST_IN_PROGRESS", true);
            }
            if (causedByConstraint(collision, "uq_analyst_conversation_exchange_processing")) {
                recordRequestConflict("CONVERSATION_BUSY");
                throw new ConversationConflictException("CONVERSATION_BUSY", true);
            }
            throw collision;
        }
        if (prepared == null) throw new IllegalStateException("Could not prepare conversation request");
        if (prepared.replayFinal() == null) {
            meters.counter("finvera.analyst.conversation.accepted").increment();
            if (prepared.created()) meters.counter("finvera.analyst.conversation.created").increment();
            if (prepared.exchange().getContextIncludedCount() > 0) {
                meters.counter("finvera.analyst.conversation.context.included")
                        .increment(prepared.exchange().getContextIncludedCount());
            }
            if (prepared.exchange().getContextOmittedCount() > 0) {
                meters.counter("finvera.analyst.conversation.context.omitted")
                        .increment(prepared.exchange().getContextOmittedCount());
            }
            if (prepared.staleReconciled() > 0) {
                meters.counter("finvera.analyst.conversation.stale-processing.reconciled")
                        .increment(prepared.staleReconciled());
            }
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onTimeout(() -> fail(prepared.exchangeId(), ownerId, "STREAM_TIMEOUT"));
        emitter.onError(error -> cancel(prepared.exchangeId(), ownerId));
        emitter.onCompletion(() -> cancel(prepared.exchangeId(), ownerId));
        try {
            send(emitter, accepted(prepared));
        } catch (RuntimeException sendFailure) {
            cancel(prepared.exchangeId(), ownerId);
            throw sendFailure;
        }
        if (prepared.replayFinal() != null) {
            send(emitter, java.util.Map.of("type", "final", "final", prepared.replayFinal()));
            emitter.complete();
            meters.counter("finvera.analyst.conversation.replayed").increment();
            return emitter;
        }

        AnalystService.StreamLifecycle lifecycle = new AnalystService.StreamLifecycle() {
            @Override public void onFinal(PublicFinalEventDto result) { complete(prepared.exchangeId(), ownerId, result); }
            @Override public void onFailure(String reasonCode) { fail(prepared.exchangeId(), ownerId, reasonCode); }
        };
        try {
            Thread.ofVirtual().name("analyst-conversation-" + prepared.exchangeId()).start(() -> {
                try {
                    analystService.processAskStream(ownerId,
                            new AskAnalystRequest(question, symbol, prepared.priorTurns()), emitter,
                            prepared.exchangeId(), lifecycle);
                } catch (Exception failure) {
                    log.error("Conversation analyst task failed for exchangeId {}, failureType={}",
                            prepared.exchangeId(), failure.getClass().getSimpleName());
                    fail(prepared.exchangeId(), ownerId, "ANALYST_UNAVAILABLE");
                    try {
                        send(emitter, Map.of("type", "error", "reasonCode", "ANALYST_UNAVAILABLE", "retryable", true));
                    } catch (RuntimeException ignored) {
                        // The durable terminal state is authoritative when the stream is already unavailable.
                    } finally {
                        emitter.complete();
                    }
                }
            });
        } catch (RuntimeException unavailable) {
            log.error("Conversation task could not start for exchangeId {}, failureType={}",
                    prepared.exchangeId(), unavailable.getClass().getSimpleName());
            fail(prepared.exchangeId(), ownerId, "ANALYST_UNAVAILABLE");
            try {
                send(emitter, Map.of("type", "error", "reasonCode", "ANALYST_UNAVAILABLE", "retryable", true));
            } finally {
                emitter.complete();
            }
        }
        return emitter;
    }

    private PreparedAsk prepare(UUID ownerId, ConversationAskRequest request, String question, String symbol) {
        AnalystConversationExchangeEntity duplicate = exchanges
                .findByOwnerIdAndClientRequestId(ownerId, request.clientRequestId()).orElse(null);
        if (duplicate != null) {
            boolean intentMatches = request.conversationId() == null
                    ? duplicate.getSequenceNo() == 1
                    : request.conversationId().equals(duplicate.getConversationId());
            if (!intentMatches || !duplicate.getQuestion().equals(question)
                    || !java.util.Objects.equals(duplicate.getSymbol(), symbol)) {
                throw new ConversationConflictException("IDEMPOTENCY_KEY_REUSED", false);
            }
            AnalystConversationEntity conversation = conversations
                    .findByIdAndOwnerId(duplicate.getConversationId(), ownerId).orElseThrow(ConversationNotFoundException::new);
            if (duplicate.getStatus() == ConversationExchangeStatus.PROCESSING) {
                throw new ConversationConflictException("REQUEST_IN_PROGRESS", true);
            }
            if (duplicate.getStatus() == ConversationExchangeStatus.COMPLETED) {
                return new PreparedAsk(conversation, duplicate, false, List.of(), finalResult(duplicate), 0);
            }
            throw new ConversationConflictException("REQUEST_ALREADY_TERMINAL", false);
        }

        Instant now = clock.instant();
        boolean created = request.conversationId() == null;
        AnalystConversationEntity conversation;
        int staleReconciled = 0;
        if (created) {
            conversation = new AnalystConversationEntity(UUID.randomUUID(), ownerId,
                    ConversationTitlePolicy.automaticTitle(question), now);
            conversations.save(conversation);
        } else {
            conversation = conversations.findOwnedForUpdate(request.conversationId(), ownerId)
                    .orElseThrow(ConversationNotFoundException::new);
            staleReconciled = reconcileStale(conversation, ownerId, now);
            if (exchanges.existsByConversationIdAndOwnerIdAndStatus(conversation.getId(), ownerId,
                    ConversationExchangeStatus.PROCESSING)) {
                throw new ConversationConflictException("CONVERSATION_BUSY", true);
            }
        }

        long sequence = conversation.allocateSequence(now);
        List<PriorTurnDto> candidates = exchanges
                .findTop10ByConversationIdAndOwnerIdAndStatusAndSequenceNoLessThanOrderBySequenceNoDesc(
                        conversation.getId(), ownerId, ConversationExchangeStatus.COMPLETED, sequence)
                .stream().limit(properties.conversationContextCandidates())
                .map(e -> new PriorTurnDto(e.getQuestion(), e.getAnswer())).toList();
        var selection = ConversationContextWindowPolicy.selectNewestFirst(candidates,
                properties.conversationContextIncluded(), properties.conversationContextCharacters());
        AnalystConversationExchangeEntity exchange = new AnalystConversationExchangeEntity(UUID.randomUUID(),
                conversation.getId(), ownerId, sequence, request.clientRequestId(), question, symbol,
                (short) selection.includedExchanges(), selection.omittedExchanges(), now);
        conversations.save(conversation);
        exchanges.save(exchange);
        return new PreparedAsk(conversation, exchange, created, selection.turns(), null, staleReconciled);
    }

    private int reconcileStale(AnalystConversationEntity conversation, UUID ownerId, Instant now) {
        Instant cutoff = now.minus(properties.askTimeout()).minus(properties.conversationStaleGrace());
        List<AnalystConversationExchangeEntity> stale = exchanges
                .findByConversationIdAndOwnerIdAndStatusAndCreatedAtBefore(conversation.getId(), ownerId,
                        ConversationExchangeStatus.PROCESSING, cutoff, PageRequest.of(0, 1));
        stale.forEach(e -> { e.fail("STALE_PROCESSING", ConversationExchangeStatus.FAILED, now); exchanges.save(e); });
        return stale.size();
    }

    private AcceptedEvent accepted(PreparedAsk prepared) {
        var exchange=prepared.exchange();
        return new AcceptedEvent("accepted", prepared.conversation().getId(), exchange.getId(), prepared.created(),
                prepared.conversation().getTitle(), new ContextWindowInfo(exchange.getContextRuleVersion(),
                exchange.getContextIncludedCount(), exchange.getContextOmittedCount()));
    }

    private void complete(UUID exchangeId, UUID ownerId, PublicFinalEventDto result) {
        Timer.Sample sample=Timer.start(meters);
        try {
            Boolean transitioned = transactions.execute(status -> {
                AnalystConversationExchangeEntity exchange=exchanges.findByIdAndOwnerId(exchangeId, ownerId)
                        .orElseThrow(ConversationNotFoundException::new);
                try {
                    boolean stateChanged = exchange.complete(result.answer(),
                            objectMapper.writeValueAsString(StoredFinalMetadata.from(result)), clock.instant());
                    if (!stateChanged) return false;
                } catch (Exception e) { throw new IllegalStateException("Invalid public Analyst result", e); }
                exchanges.save(exchange);
                conversations.findOwnedForUpdate(exchange.getConversationId(), ownerId).ifPresent(c -> {
                    c.touch(clock.instant()); conversations.save(c);
                });
                return true;
            });
            if (Boolean.TRUE.equals(transitioned)) {
                String outcome = result.refused() ? "refused" : result.toolCallBoundReached() ? "partial" : "completed";
                meters.counter("finvera.analyst.conversation.ask.completed", "outcome", outcome).increment();
            }
        } finally {
            sample.stop(meters.timer("finvera.analyst.conversation.persist-final"));
        }
    }

    private void fail(UUID exchangeId, UUID ownerId, String code) {
        String safeCode = safeFailureCode(code);
        Boolean transitioned = transactions.execute(status -> exchanges.findByIdAndOwnerId(exchangeId, ownerId)
                .map(e -> {
                    if (!e.fail(safeCode, ConversationExchangeStatus.FAILED, clock.instant())) return false;
                    exchanges.save(e);
                    return true;
                }).orElse(false));
        if (Boolean.TRUE.equals(transitioned)) {
            meters.counter("finvera.analyst.conversation.failed", "reason", safeCode).increment();
        }
    }

    private void cancel(UUID exchangeId, UUID ownerId) {
        Boolean transitioned = transactions.execute(status -> exchanges.findByIdAndOwnerId(exchangeId, ownerId)
                .map(e -> {
                    if (!e.fail("CLIENT_DISCONNECTED", ConversationExchangeStatus.CANCELLED, clock.instant())) return false;
                    exchanges.save(e);
                    return true;
                }).orElse(false));
        if (Boolean.TRUE.equals(transitioned)) meters.counter("finvera.analyst.conversation.cancelled").increment();
    }

    public ConversationPage list(UUID ownerId, String cursor, int requestedLimit) {
        Timer.Sample sample = Timer.start(meters);
        try {
            int limit=Math.max(1, Math.min(requestedLimit, 50));
            ConversationCursor before=decodeConversationCursor(cursor);
            List<AnalystConversationEntity> rows=before == null
                    ? conversations.findFirstPage(ownerId, PageRequest.of(0, limit + 1))
                    : conversations.findPageBefore(ownerId, before.at(), before.id(), PageRequest.of(0, limit + 1));
            boolean more=rows.size()>limit; if (more) rows=new ArrayList<>(rows.subList(0, limit));
            Map<UUID, SummaryStats> stats=summaryStats(rows, ownerId);
            List<ConversationSummary> items=rows.stream()
                    .map(c -> summary(c, stats.getOrDefault(c.getId(), SummaryStats.EMPTY))).toList();
            String next=more && !rows.isEmpty() ? encodeConversationCursor(rows.getLast()) : null;
            return new ConversationPage(items, next, more);
        } finally {
            sample.stop(meters.timer("finvera.analyst.conversation.list"));
        }
    }

    public ExchangePage read(UUID ownerId, UUID conversationId, String before, int requestedLimit) {
        Timer.Sample sample = Timer.start(meters);
        try {
            AnalystConversationEntity conversation=conversations.findByIdAndOwnerId(conversationId, ownerId)
                    .orElseThrow(ConversationNotFoundException::new);
            int limit=Math.max(1, Math.min(requestedLimit, 100));
            Long beforeSequence=decodeExchangeCursor(before);
            List<AnalystConversationExchangeEntity> rows=beforeSequence == null
                    ? exchanges.findFirstPage(conversationId, ownerId, PageRequest.of(0, limit + 1))
                    : exchanges.findPageBefore(conversationId, ownerId, beforeSequence, PageRequest.of(0, limit + 1));
            boolean more=rows.size()>limit; if (more) rows=new ArrayList<>(rows.subList(0, limit));
            Long oldest=rows.isEmpty() ? null : rows.getLast().getSequenceNo();
            Collections.reverse(rows);
            return new ExchangePage(summary(conversation, ownerId), rows.stream().map(this::exchangeDto).toList(),
                    more ? encodeExchangeCursor(oldest) : null, more);
        } finally {
            sample.stop(meters.timer("finvera.analyst.conversation.read"));
        }
    }

    public ConversationSummary rename(UUID ownerId, UUID conversationId, String rawTitle) {
        return transactions.execute(status -> {
            AnalystConversationEntity conversation=conversations.findOwnedForUpdate(conversationId, ownerId)
                    .orElseThrow(ConversationNotFoundException::new);
            conversation.rename(ConversationTitlePolicy.ownerTitle(rawTitle), clock.instant());
            return summary(conversations.save(conversation), ownerId);
        });
    }

    public void delete(UUID ownerId, UUID conversationId) {
        transactions.executeWithoutResult(status -> {
            AnalystConversationEntity conversation=conversations.findOwnedForUpdate(conversationId, ownerId)
                    .orElseThrow(ConversationNotFoundException::new);
            if (exchanges.existsByConversationIdAndOwnerIdAndStatus(conversationId, ownerId,
                    ConversationExchangeStatus.PROCESSING)) {
                throw new ConversationConflictException("CONVERSATION_BUSY", true);
            }
            conversations.delete(conversation);
        });
        meters.counter("finvera.analyst.conversation.deleted").increment();
    }

    private ConversationSummary summary(AnalystConversationEntity c, UUID ownerId) {
        return summary(c, new SummaryStats(exchanges.countByConversationIdAndOwnerId(c.getId(), ownerId),
                exchanges.existsByConversationIdAndOwnerIdAndStatus(c.getId(), ownerId, ConversationExchangeStatus.PROCESSING)));
    }
    private ConversationSummary summary(AnalystConversationEntity c, SummaryStats stats) {
        return new ConversationSummary(c.getId(), c.getTitle(), c.getTitleSource(), c.getCreatedAt(), c.getUpdatedAt(),
                c.getLastActivityAt(), stats.count(), stats.processing());
    }
    private Map<UUID, SummaryStats> summaryStats(List<AnalystConversationEntity> rows, UUID ownerId) {
        if (rows.isEmpty()) return Map.of();
        Map<UUID, SummaryStats> result=new HashMap<>();
        exchanges.summarize(ownerId, rows.stream().map(AnalystConversationEntity::getId).toList()).forEach(row ->
                result.put((UUID) row[0], new SummaryStats(((Number) row[1]).longValue(), ((Number) row[2]).longValue() > 0)));
        return result;
    }

    private ConversationExchange exchangeDto(AnalystConversationExchangeEntity e) {
        return new ConversationExchange(e.getId(), e.getSequenceNo(), e.getQuestion(), e.getSymbol(), e.getStatus().name(),
                e.getStatus()==ConversationExchangeStatus.COMPLETED ? finalResult(e) : null, e.getFailureCode(),
                new ContextWindowInfo(e.getContextRuleVersion(), e.getContextIncludedCount(), e.getContextOmittedCount()),
                e.getCreatedAt(), e.getCompletedAt());
    }

    private PublicFinalEventDto finalResult(AnalystConversationExchangeEntity e) {
        try {
            StoredFinalMetadata metadata=objectMapper.readValue(e.getResponseMetadata(), StoredFinalMetadata.class);
            return metadata.toFinal(e.getAnswer());
        }
        catch (Exception ex) { throw new IllegalStateException("Stored Analyst result is invalid", ex); }
    }

    private static String normalizeQuestion(String value) {
        if (value==null || value.isBlank()) throw new IllegalArgumentException("Question must not be blank");
        String q=value.strip(); if (q.length()>2000) throw new IllegalArgumentException("Question must not exceed 2000 characters");
        return q;
    }
    private static String normalizeSymbol(String value) {
        if (value==null || value.isBlank()) return null;
        String symbol=value.strip().toUpperCase(Locale.ROOT);
        if (symbol.length()>20 || !symbol.matches("[A-Z0-9._-]+")) throw new IllegalArgumentException("Invalid symbol");
        return symbol;
    }
    private static String safeFailureCode(String code) {
        return code != null && code.matches("[A-Z0-9_]{1,64}") ? code : "ANALYST_FAILURE";
    }
    private static boolean causedByConstraint(Throwable failure, String constraintName) {
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(constraintName)) return true;
            current = current.getCause();
        }
        return false;
    }
    private void recordRequestConflict(String reasonCode) {
        meters.counter("finvera.analyst.conversation.request.conflict", "reason", safeFailureCode(reasonCode)).increment();
    }
    private void send(SseEmitter emitter, Object value) {
        try { emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(value))); }
        catch (IOException e) { throw new IllegalStateException("Could not start conversation stream", e); }
    }
    private static String encodeConversationCursor(AnalystConversationEntity c) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (c.getLastActivityAt()+"|"+c.getId()).getBytes(StandardCharsets.UTF_8));
    }
    private static ConversationCursor decodeConversationCursor(String value) {
        if (value==null || value.isBlank()) return null;
        try { String[] p=new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|",2);
            return new ConversationCursor(Instant.parse(p[0]), UUID.fromString(p[1]));
        } catch (Exception e) { throw new IllegalArgumentException("Invalid conversation cursor"); }
    }
    private static String encodeExchangeCursor(Long sequence) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(sequence).getBytes(StandardCharsets.UTF_8));
    }
    private static Long decodeExchangeCursor(String value) {
        if (value==null || value.isBlank()) return null;
        try { long decoded=Long.parseLong(new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8));
            if (decoded < 1) throw new IllegalArgumentException(); return decoded;
        } catch (Exception e) { throw new IllegalArgumentException("Invalid exchange cursor"); }
    }

    private record PreparedAsk(AnalystConversationEntity conversation, AnalystConversationExchangeEntity exchange,
            boolean created, List<PriorTurnDto> priorTurns, PublicFinalEventDto replayFinal, int staleReconciled) {
        UUID exchangeId(){return exchange.getId();}
    }
    private record ConversationCursor(Instant at, UUID id) { }
    private record SummaryStats(long count, boolean processing) {
        private static final SummaryStats EMPTY=new SummaryStats(0, false);
    }
    private record StoredFinalMetadata(List<PublicStructuredClaimDto> structuredClaims,
            List<PublicDocumentClaimDto> documentClaims, boolean refused, List<ToolCallEventDto> toolCalls,
            boolean toolCallBoundReached, String ruleVersion, String synthesisMode, String plannerMode,
            ClaimCoverage claimCoverage) {
        static StoredFinalMetadata from(PublicFinalEventDto result) {
            return new StoredFinalMetadata(result.structuredClaims(), result.documentClaims(), result.refused(),
                    result.toolCalls(), result.toolCallBoundReached(), result.ruleVersion(), result.synthesisMode(),
                    result.plannerMode(), result.claimCoverage());
        }
        PublicFinalEventDto toFinal(String answer) {
            return new PublicFinalEventDto(answer, structuredClaims, documentClaims, refused, toolCalls,
                    toolCallBoundReached, ruleVersion, synthesisMode, plannerMode, claimCoverage);
        }
    }
}
