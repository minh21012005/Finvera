package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderObservation;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderSnapshotBatch;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsThesisFrameMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Normalizes and persists TCBS Thesis current-session index and breadth events. */
public final class TcbsLiveMarketIngestionService implements Consumer<TcbsThesisFrameMapper.Event> {
    public static final String SOURCE = "TCBS_IFLASH_THESIS";
    private static final String UNIVERSE_VERSION = "tcbs-thesis-exchange-aggregate-v1";
    private static final List<String> STREAM_REASONS = List.of(
            "TCBS_STREAM_RECEIVE_TIME", "TCBS_STREAM_ORDERING_UNAVAILABLE");
    private final MarketIngestionService ingestion;
    private final BreadthService breadth;
    private final MarketReferenceDataService referenceData;
    private final LiveMarketRegimeReconciliationService regimeReconciliation;
    private final Map<Integer, TcbsThesisFrameMapper.IndexUpdate> latestBreadth = new ConcurrentHashMap<>();

    public TcbsLiveMarketIngestionService(MarketIngestionService ingestion, BreadthService breadth,
            MarketReferenceDataService referenceData, LiveMarketRegimeReconciliationService regimeReconciliation) {
        this.ingestion = ingestion;
        this.breadth = breadth;
        this.referenceData = referenceData;
        this.regimeReconciliation = regimeReconciliation;
    }

    @Override
    public void accept(TcbsThesisFrameMapper.Event event) {
        if (!(event instanceof TcbsThesisFrameMapper.IndexUpdate update)) return;
        String venue = venue(update.code());
        var session = referenceData.resolveSession(venue, update.receivedAt());
        Instant bucket = bucket(update.receivedAt());
        ingestIndex(update, session.tradingDate(), session.state(), bucket);
        if (update.breadth() != null) {
            latestBreadth.put(update.indexNumber(), update);
            persistBreadthIfCoherent(session.tradingDate());
        }
    }

    private void ingestIndex(TcbsThesisFrameMapper.IndexUpdate update, LocalDate tradingDate,
            SessionState sessionState, Instant observedAt) {
        var observation = new ProviderObservation(update.code(), update.level(), update.referenceLevel(),
                update.absoluteChange(), update.percentageChange(), update.matchedVolume(),
                update.matchedValueVnd(), STREAM_REASONS);
        ingestion.ingest(new ProviderSnapshotBatch(SOURCE, tradingDate, observedAt, sessionState,
                sessionState == SessionState.UNKNOWN ? DataStatus.PARTIAL : DataStatus.CURRENT,
                STREAM_REASONS, List.of(observation)));
    }

    private void persistBreadthIfCoherent(LocalDate tradingDate) {
        Map<Integer, TcbsThesisFrameMapper.IndexUpdate> required = new ConcurrentHashMap<>();
        for (int indexNumber : List.of(1, 3, 5)) {
            var update = latestBreadth.get(indexNumber);
            if (update == null) return;
            required.put(indexNumber, update);
        }
        Instant oldest = required.values().stream().map(TcbsThesisFrameMapper.IndexUpdate::receivedAt)
                .min(Instant::compareTo).orElseThrow();
        Instant newest = required.values().stream().map(TcbsThesisFrameMapper.IndexUpdate::receivedAt)
                .max(Instant::compareTo).orElseThrow();
        if (newest.isAfter(oldest.plusSeconds(30))) return;
        Instant bucket = bucket(newest);
        int advancing = sum(required, TcbsThesisFrameMapper.BreadthCounts::advancing);
        int declining = sum(required, TcbsThesisFrameMapper.BreadthCounts::declining);
        int unchanged = sum(required, TcbsThesisFrameMapper.BreadthCounts::unchanged);
        var result = new BreadthCalculator.Result(advancing, declining, unchanged, 0,
                advancing + declining + unchanged, List.of("PROVIDER_AGGREGATE_BREADTH"));
        breadth.persistProviderAggregate(tradingDate, bucket, UNIVERSE_VERSION, result,
                breadthHash(tradingDate, bucket, required))
                .ifPresent(snapshot -> regimeReconciliation.reconcile(tradingDate, snapshot));
    }

    private static int sum(Map<Integer, TcbsThesisFrameMapper.IndexUpdate> updates,
            java.util.function.ToIntFunction<TcbsThesisFrameMapper.BreadthCounts> value) {
        return updates.values().stream().map(TcbsThesisFrameMapper.IndexUpdate::breadth)
                .mapToInt(value).sum();
    }

    private static Instant bucket(Instant instant) {
        long epoch = instant.getEpochSecond();
        return Instant.ofEpochSecond(epoch - Math.floorMod(epoch, 30));
    }

    private static String venue(IndexCode code) {
        return switch (code) {
            case VN_INDEX, VN30 -> "HOSE";
            case HNX_INDEX -> "HNX";
            case UPCOM_INDEX -> "UPCOM";
        };
    }

    private static String breadthHash(LocalDate tradingDate, Instant bucket,
            Map<Integer, TcbsThesisFrameMapper.IndexUpdate> updates) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, tradingDate.toString());
            update(digest, bucket.toString());
            updates.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                var counts = entry.getValue().breadth();
                update(digest, entry.getKey() + ":" + counts.advancing() + ":" + counts.declining()
                        + ":" + counts.unchanged());
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
