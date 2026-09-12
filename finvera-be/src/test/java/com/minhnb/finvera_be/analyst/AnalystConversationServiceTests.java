package com.minhnb.finvera_be.analyst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.minhnb.finvera_be.analyst.dto.AnalystConversationDtos.ConversationAskRequest;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.ClaimCoverage;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.PublicFinalEventDto;
import com.minhnb.finvera_be.analyst.repository.AnalystConversationExchangeRepository;
import com.minhnb.finvera_be.analyst.repository.AnalystConversationRepository;
import com.minhnb.finvera_be.analyst.service.AnalystConversationExceptions.ConversationConflictException;
import com.minhnb.finvera_be.analyst.service.AnalystConversationExceptions.ConversationNotFoundException;
import com.minhnb.finvera_be.analyst.service.AnalystConversationService;
import com.minhnb.finvera_be.analyst.service.AnalystService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class AnalystConversationServiceTests {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
    private static final UUID OWNER=UUID.randomUUID();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("finvera.security.owner.id", () -> OWNER);
        registry.add("finvera.security.owner.username", () -> "owner-" + UUID.randomUUID());
        registry.add("finvera.security.owner.password-hash",
                () -> new BCryptPasswordEncoder(4).encode(UUID.randomUUID().toString()));
    }

    @Autowired AnalystConversationService service;
    @Autowired AnalystConversationRepository conversations;
    @Autowired AnalystConversationExchangeRepository exchanges;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry meterRegistry;
    @MockitoBean AnalystService analystService;

    @BeforeEach
    void clean() {
        conversations.deleteAll();
    }

    @Test
    void createsCompletesAndReplaysExactlyOnce() throws Exception {
        CountDownLatch completed=new CountDownLatch(1);
        doAnswer(invocation -> {
            AnalystService.StreamLifecycle lifecycle=invocation.getArgument(4);
            lifecycle.onFinal(finalResult("Kết quả đã kiểm chứng"));
            completed.countDown();
            return null;
        }).when(analystService).processAskStream(eq(OWNER), any(), any(), any(), any());

        UUID requestId=UUID.randomUUID();
        service.ask(OWNER, new ConversationAskRequest(null, requestId, "Phân tích FPT", "fpt"));
        assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(exchanges.count()).isOne();
        assertThat(exchanges.findByOwnerIdAndClientRequestId(OWNER, requestId).orElseThrow().getAnswer())
                .isEqualTo("Kết quả đã kiểm chứng");

        service.ask(OWNER, new ConversationAskRequest(null, requestId, "Phân tích FPT", "FPT"));
        verify(analystService, times(1)).processAskStream(eq(OWNER), any(), any(), any(), any());
        assertThat(exchanges.count()).isOne();
        assertThat(meterRegistry.find("finvera.analyst.conversation.created").counter()).isNotNull();
        assertThat(meterRegistry.find("finvera.analyst.conversation.ask.completed")
                .tag("outcome", "completed").counter()).isNotNull();
        assertThat(meterRegistry.find("finvera.analyst.conversation.replayed").counter()).isNotNull();
    }

    @Test
    void rejectsReusedRequestIdWithDifferentPayloadWithoutReplay() throws Exception {
        CountDownLatch completed=new CountDownLatch(1);
        doAnswer(invocation -> {
            AnalystService.StreamLifecycle lifecycle=invocation.getArgument(4);
            lifecycle.onFinal(finalResult("Answer"));
            completed.countDown();
            return null;
        }).when(analystService).processAskStream(eq(OWNER), any(), any(), any(), any());
        UUID requestId=UUID.randomUUID();
        service.ask(OWNER, new ConversationAskRequest(null, requestId, "Question one", null));
        assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.ask(OWNER,
                new ConversationAskRequest(null, requestId, "Different question", null)))
                .isInstanceOf(ConversationConflictException.class)
                .satisfies(ex -> assertThat(((ConversationConflictException) ex).reasonCode())
                        .isEqualTo("IDEMPOTENCY_KEY_REUSED"));
        assertThat(meterRegistry.find("finvera.analyst.conversation.request.conflict")
                .tag("reason", "IDEMPOTENCY_KEY_REUSED").counter()).isNotNull();
    }

    @RepeatedTest(3)
    void listsReadsContinuesRenamesAndDeletesOnlyForOwner() throws Exception {
        CountDownLatch firstCompleted=new CountDownLatch(1);
        AtomicReference<AnalystService.StreamLifecycle> secondLifecycle=new AtomicReference<>();
        AtomicReference<com.minhnb.finvera_be.analyst.dto.AskAnalystDto.AskAnalystRequest> secondRequest=
                new AtomicReference<>();
        doAnswer(invocation -> {
            var request=invocation.<com.minhnb.finvera_be.analyst.dto.AskAnalystDto.AskAnalystRequest>getArgument(1);
            AnalystService.StreamLifecycle lifecycle=invocation.getArgument(4);
            if (firstCompleted.getCount() > 0) {
                lifecycle.onFinal(finalResult("First verified answer"));
                firstCompleted.countDown();
            } else {
                secondRequest.set(request);
                secondLifecycle.set(lifecycle);
            }
            return null;
        }).when(analystService).processAskStream(eq(OWNER), any(), any(), any(), any());

        service.ask(OWNER, new ConversationAskRequest(null, UUID.randomUUID(), "First question", "fpt"));
        assertThat(firstCompleted.await(10, TimeUnit.SECONDS)).isTrue();

        var page=service.list(OWNER, null, 20);
        assertThat(page.items()).hasSize(1);
        UUID conversationId=page.items().getFirst().id();
        var opened=service.read(OWNER, conversationId, null, 50);
        assertThat(opened.items()).hasSize(1);
        assertThat(opened.items().getFirst().finalResult().answer()).isEqualTo("First verified answer");
        assertThatThrownBy(() -> service.read(UUID.randomUUID(), conversationId, null, 50))
                .isInstanceOf(ConversationNotFoundException.class);

        service.ask(OWNER, new ConversationAskRequest(conversationId, UUID.randomUUID(), "Follow up", "FPT"));
        for (int i=0; i<100 && secondRequest.get()==null; i++) Thread.sleep(20);
        assertThat(secondRequest.get()).isNotNull();
        assertThat(secondRequest.get().priorTurns()).singleElement().satisfies(turn -> {
            assertThat(turn.question()).isEqualTo("First question");
            assertThat(turn.answer()).isEqualTo("First verified answer");
        });
        secondLifecycle.get().onFinal(finalResult("Second verified answer"));

        var newestPage=service.read(OWNER, conversationId, null, 1);
        assertThat(newestPage.hasMore()).isTrue();
        assertThat(newestPage.items()).extracting(item -> item.sequenceNo()).containsExactly(2L);
        var olderPage=service.read(OWNER, conversationId, newestPage.olderCursor(), 1);
        assertThat(olderPage.items()).extracting(item -> item.sequenceNo()).containsExactly(1L);
        assertThat(olderPage.hasMore()).isFalse();
        assertThat(meterRegistry.find("finvera.analyst.conversation.list").timer()).isNotNull();
        assertThat(meterRegistry.find("finvera.analyst.conversation.read").timer()).isNotNull();
        assertThat(service.rename(OWNER, conversationId, "  My research  ").title()).isEqualTo("My research");
        service.delete(OWNER, conversationId);
        assertThat(meterRegistry.find("finvera.analyst.conversation.context.included").counter()).isNotNull();
        assertThat(meterRegistry.find("finvera.analyst.conversation.deleted").counter()).isNotNull();
        assertThatThrownBy(() -> service.read(OWNER, conversationId, null, 50))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void blocksParallelAskAndPersistsTerminalFailure() throws Exception {
        AtomicReference<AnalystService.StreamLifecycle> lifecycleRef=new AtomicReference<>();
        doAnswer(invocation -> {
            lifecycleRef.set(invocation.getArgument(4));
            return null;
        }).when(analystService).processAskStream(eq(OWNER), any(), any(), any(), any());

        service.ask(OWNER, new ConversationAskRequest(null, UUID.randomUUID(), "Long request", null));
        for (int i=0; i<100 && lifecycleRef.get()==null; i++) Thread.sleep(20);
        UUID conversationId=service.list(OWNER, null, 20).items().getFirst().id();
        assertThatThrownBy(() -> service.ask(OWNER,
                new ConversationAskRequest(conversationId, UUID.randomUUID(), "Parallel request", null)))
                .isInstanceOf(ConversationConflictException.class)
                .satisfies(ex -> assertThat(((ConversationConflictException) ex).reasonCode())
                        .isEqualTo("CONVERSATION_BUSY"));

        String privateProviderFailure = "provider-secret-canary";
        lifecycleRef.get().onFailure(privateProviderFailure);
        var exchange=service.read(OWNER, conversationId, null, 50).items().getFirst();
        assertThat(exchange.status()).isEqualTo("FAILED");
        assertThat(exchange.failureCode()).isEqualTo("ANALYST_FAILURE");
        assertThat(meterRegistry.getMeters()).flatExtracting(meter -> meter.getId().getTags())
                .noneMatch(tag -> tag.getValue().contains(privateProviderFailure));
    }

    @Test
    void reconcilesStaleProcessingBeforeAcceptingTheNextQuestion() throws Exception {
        java.util.concurrent.atomic.AtomicInteger invocations=new java.util.concurrent.atomic.AtomicInteger();
        AtomicReference<AnalystService.StreamLifecycle> latestLifecycle=new AtomicReference<>();
        doAnswer(invocation -> {
            latestLifecycle.set(invocation.getArgument(4));
            invocations.incrementAndGet();
            return null;
        }).when(analystService).processAskStream(eq(OWNER), any(), any(), any(), any());

        service.ask(OWNER, new ConversationAskRequest(null, UUID.randomUUID(), "Interrupted request", null));
        for (int i=0; i<100 && invocations.get()<1; i++) Thread.sleep(20);
        UUID conversationId=service.list(OWNER, null, 20).items().getFirst().id();
        jdbc.update("update analyst_conversation_exchange set created_at=now()-interval '10 minutes' where conversation_id=?",
                conversationId);

        service.ask(OWNER, new ConversationAskRequest(conversationId, UUID.randomUUID(), "Recovered request", null));
        for (int i=0; i<100 && invocations.get()<2; i++) Thread.sleep(20);
        assertThat(invocations.get()).isEqualTo(2);
        assertThat(service.read(OWNER, conversationId, null, 50).items())
                .extracting(item -> item.status()).containsExactly("FAILED", "PROCESSING");
        assertThat(meterRegistry.find("finvera.analyst.conversation.stale-processing.reconciled").counter()).isNotNull();
        latestLifecycle.get().onFailure("TEST_END");
    }

    private static PublicFinalEventDto finalResult(String answer) {
        return new PublicFinalEventDto(answer, List.of(), List.of(), false, List.of(), false,
                "orchestration-v1", "ONLINE", "MODEL", ClaimCoverage.FULL);
    }
}
