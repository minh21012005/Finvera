package com.minhnb.finvera_be.analyst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.analyst.domain.AnalystQueryOutcome;
import com.minhnb.finvera_be.analyst.domain.AnalystRequestType;
import com.minhnb.finvera_be.analyst.domain.ToolCallStatus;
import com.minhnb.finvera_be.analyst.domain.ToolName;
import com.minhnb.finvera_be.analyst.entity.AnalystQueryEntity;
import com.minhnb.finvera_be.analyst.entity.AnalystToolCallEntity;
import com.minhnb.finvera_be.analyst.repository.AnalystQueryRepository;
import com.minhnb.finvera_be.analyst.repository.AnalystToolCallRepository;
import com.minhnb.finvera_be.analyst.service.AnalystQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnalystQueryServiceTests {

    private AnalystQueryRepository queryRepository;
    private AnalystToolCallRepository toolCallRepository;
    private AnalystQueryService service;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        queryRepository = mock(AnalystQueryRepository.class);
        toolCallRepository = mock(AnalystToolCallRepository.class);
        fixedClock = Clock.fixed(Instant.parse("2026-08-20T10:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new AnalystQueryService(queryRepository, toolCallRepository, fixedClock);
    }

    @Test
    void recordQueryStart_truncatesPreviewAndCalculatesSha256() {
        UUID queryId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String longQuestion = "a".repeat(500);

        when(queryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalystQueryEntity result = service.recordQueryStart(queryId, ownerId, AnalystRequestType.ASK, longQuestion);

        assertThat(result.getId()).isEqualTo(queryId);
        assertThat(result.getQuestionPreview()).hasSize(300);
        assertThat(result.getQuestionPreview()).isEqualTo("a".repeat(300));
        assertThat(result.getQuestionHash()).hasSize(64);
        assertThat(result.getOutcome()).isEqualTo(AnalystQueryOutcome.PARTIAL);
        assertThat(result.isToolCallBoundReached()).isFalse();
        assertThat(result.getRequestedAt()).isEqualTo(fixedClock.instant());
        assertThat(result.getCompletedAt()).isNull();
    }

    @Test
    void recordToolCall_savesEntityProperly() {
        UUID queryId = UUID.randomUUID();
        when(toolCallRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalystToolCallEntity entity = service.recordToolCall(
                queryId,
                (short) 1,
                ToolName.TECHNICAL,
                "{\"symbol\":\"HPG\"}",
                ToolCallStatus.SUCCEEDED,
                null,
                150,
                fixedClock.instant());

        assertThat(entity.getAnalystQueryId()).isEqualTo(queryId);
        assertThat(entity.getSequenceNo()).isEqualTo((short) 1);
        assertThat(entity.getToolName()).isEqualTo(ToolName.TECHNICAL);
        assertThat(entity.getStatus()).isEqualTo(ToolCallStatus.SUCCEEDED);
        assertThat(entity.getFailureReason()).isNull();
        assertThat(entity.getLatencyMs()).isEqualTo(150);
    }

    @Test
    void recordQueryCompletion_updatesOutcomeAndCompletedAt() {
        UUID queryId = UUID.randomUUID();
        AnalystQueryEntity existing = new AnalystQueryEntity(
                queryId,
                UUID.randomUUID(),
                AnalystRequestType.ASK,
                "preview",
                "hash",
                AnalystQueryOutcome.PARTIAL,
                false,
                fixedClock.instant(),
                null);

        when(queryRepository.findById(queryId)).thenReturn(Optional.of(existing));

        service.recordQueryCompletion(queryId, AnalystQueryOutcome.COMPLETED, true);

        ArgumentCaptor<AnalystQueryEntity> captor = ArgumentCaptor.forClass(AnalystQueryEntity.class);
        verify(queryRepository).save(captor.capture());

        AnalystQueryEntity saved = captor.getValue();
        assertThat(saved.getOutcome()).isEqualTo(AnalystQueryOutcome.COMPLETED);
        assertThat(saved.isToolCallBoundReached()).isTrue();
        assertThat(saved.getCompletedAt()).isEqualTo(fixedClock.instant());
    }
}
