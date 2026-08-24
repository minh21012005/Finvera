package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderSnapshotBatch;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsThesisFrameMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TcbsLiveMarketIngestionServiceTests {
    @Mock MarketIngestionService ingestion;
    @Mock BreadthService breadth;
    @Mock MarketReferenceDataService referenceData;
    @Mock LiveMarketRegimeReconciliationService regimeReconciliation;
    private TcbsLiveMarketIngestionService service;
    private final Instant receivedAt = Instant.parse("2026-08-24T03:00:17Z");
    private final LocalDate tradingDate = LocalDate.of(2026, 8, 24);

    @BeforeEach
    void setUp() {
        when(referenceData.resolveSession(any(), any()))
                .thenReturn(new MarketReferenceDataService.SessionContext(SessionState.OPEN, tradingDate));
        lenient().when(breadth.persistProviderAggregate(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        service = new TcbsLiveMarketIngestionService(ingestion, breadth, referenceData, regimeReconciliation);
    }

    @Test
    void ingestsExactIndexLevelUsingReceiveTimeBucketAndDocumentedReasons() {
        service.accept(index(1, IndexCode.VN_INDEX, "1728.25", "12.50", 246, 58, 63));

        ArgumentCaptor<ProviderSnapshotBatch> batch = ArgumentCaptor.forClass(ProviderSnapshotBatch.class);
        verify(ingestion).ingest(batch.capture());
        assertThat(batch.getValue().source()).isEqualTo("TCBS_IFLASH_THESIS");
        assertThat(batch.getValue().tradingDate()).isEqualTo(tradingDate);
        assertThat(batch.getValue().observedAt()).isEqualTo(Instant.parse("2026-08-24T03:00:00Z"));
        assertThat(batch.getValue().sessionState()).isEqualTo(SessionState.OPEN);
        assertThat(batch.getValue().reasonCodes()).containsExactly(
                "TCBS_STREAM_RECEIVE_TIME", "TCBS_STREAM_ORDERING_UNAVAILABLE");
        assertThat(batch.getValue().observations().getFirst().level()).isEqualByComparingTo("1728.25");
        assertThat(batch.getValue().observations().getFirst().referenceLevel()).isEqualByComparingTo("1715.75");
    }

    @Test
    void persistsConsolidatedBreadthOnlyAfterHoseHnxAndUpcomAndNeverAddsVn30() {
        service.accept(index(1, IndexCode.VN_INDEX, "1728", "10", 200, 100, 50));
        service.accept(index(2, IndexCode.VN30, "1900", "15", 20, 10, 0));
        service.accept(index(3, IndexCode.HNX_INDEX, "300", "2", 80, 40, 20));
        verify(breadth, never()).persistProviderAggregate(any(), any(), any(), any(), any());

        service.accept(index(5, IndexCode.UPCOM_INDEX, "100", "1", 120, 60, 30));

        ArgumentCaptor<BreadthCalculator.Result> result = ArgumentCaptor.forClass(BreadthCalculator.Result.class);
        verify(breadth).persistProviderAggregate(eq(tradingDate), eq(Instant.parse("2026-08-24T03:00:00Z")),
                eq("tcbs-thesis-exchange-aggregate-v1"), result.capture(), any());
        assertThat(result.getValue().advancing()).isEqualTo(400);
        assertThat(result.getValue().declining()).isEqualTo(200);
        assertThat(result.getValue().unchanged()).isEqualTo(100);
        assertThat(result.getValue().eligible()).isEqualTo(700);
    }

    @Test
    void coalescesVenueBreadthAcrossAThirtySecondBucketBoundary() {
        service.accept(index(1, IndexCode.VN_INDEX, "1728", "10", 200, 100, 50,
                Instant.parse("2026-08-24T03:00:29Z")));
        service.accept(index(3, IndexCode.HNX_INDEX, "300", "2", 80, 40, 20,
                Instant.parse("2026-08-24T03:00:31Z")));
        service.accept(index(5, IndexCode.UPCOM_INDEX, "100", "1", 120, 60, 30,
                Instant.parse("2026-08-24T03:00:35Z")));

        verify(breadth).persistProviderAggregate(eq(tradingDate),
                eq(Instant.parse("2026-08-24T03:00:30Z")),
                eq("tcbs-thesis-exchange-aggregate-v1"), any(), any());
    }

    @Test
    void triggersRegimeReconciliationOnlyForANewlyPersistedCoherentBreadthBucket() {
        var persisted = new BreadthService.Snapshot(java.util.UUID.randomUUID(), tradingDate,
                Instant.parse("2026-08-24T03:00:00Z"), com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.CURRENT,
                new BreadthCalculator.Result(400, 200, 100, 0, 700, java.util.List.of()), "provider", "b".repeat(64));
        when(breadth.persistProviderAggregate(any(), any(), any(), any(), any())).thenReturn(Optional.of(persisted));

        service.accept(index(1, IndexCode.VN_INDEX, "1728", "10", 200, 100, 50));
        service.accept(index(3, IndexCode.HNX_INDEX, "300", "2", 80, 40, 20));
        service.accept(index(5, IndexCode.UPCOM_INDEX, "100", "1", 120, 60, 30));

        verify(regimeReconciliation).reconcile(tradingDate, persisted);
    }

    private TcbsThesisFrameMapper.IndexUpdate index(int number, IndexCode code, String level, String change,
            int advancing, int declining, int unchanged) {
        return index(number, code, level, change, advancing, declining, unchanged, receivedAt);
    }

    private TcbsThesisFrameMapper.IndexUpdate index(int number, IndexCode code, String level, String change,
            int advancing, int declining, int unchanged, Instant at) {
        BigDecimal levelValue = new BigDecimal(level);
        BigDecimal changeValue = new BigDecimal(change);
        return new TcbsThesisFrameMapper.IndexUpdate(number, code, levelValue,
                levelValue.subtract(changeValue), changeValue, null, 1000L,
                new BigDecimal("2000000"), new TcbsThesisFrameMapper.BreadthCounts(advancing, declining, unchanged),
                "5", at);
    }
}
