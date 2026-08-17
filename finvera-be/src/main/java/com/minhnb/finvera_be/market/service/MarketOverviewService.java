package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.domain.index.IndexOverview;
import com.minhnb.finvera_be.market.domain.index.IndexOverviewCalculator;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.domain.time.MarketFreshnessPolicy;
import com.minhnb.finvera_be.market.repository.MarketOverviewRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles one read-only, coherent market boundary; it never fabricates missing facts. */
@Service
public class MarketOverviewService {

    public static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String NO_ACCEPTED_DATA = "NO_ACCEPTED_INDEX_DATA";

    private final MarketOverviewRepository repository;
    private final BreadthService breadthService;
    private final RegimeAssessmentService regimeAssessmentService;
    private final Clock clock;
    private final IndexOverviewCalculator calculator = new IndexOverviewCalculator();
    private final MarketFreshnessPolicy freshness = new MarketFreshnessPolicy();

    public MarketOverviewService(MarketOverviewRepository repository, BreadthService breadthService,
            RegimeAssessmentService regimeAssessmentService, Clock clock) {
        this.repository = repository;
        this.breadthService = breadthService;
        this.regimeAssessmentService = regimeAssessmentService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MarketOverview latest() {
        Instant generatedAt = clock.instant();
        List<MarketOverviewRepository.LatestIndexSnapshot> rows = repository.findLatestAcceptedIndexBoundary();
        if (rows.isEmpty()) {
            return empty(generatedAt);
        }

        LocalDate tradingDate = rows.getFirst().getTradingDate();
        if (rows.stream().anyMatch(row -> !tradingDate.equals(row.getTradingDate()))) {
            throw new IllegalStateException("Market overview query returned mixed trading dates");
        }
        Instant asOf = rows.stream().map(MarketOverviewRepository.LatestIndexSnapshot::getObservedAt)
                .max(Instant::compareTo).orElseThrow();
        SessionState session = commonSession(rows);
        DataStatus freshnessStatus = freshness.evaluate(asOf,
                generatedAt.isBefore(asOf) ? asOf : generatedAt, Duration.ZERO, session, DataStatus.CURRENT);
        String source = commonSource(rows);
        long revision = rows.stream().map(MarketOverviewRepository.LatestIndexSnapshot::getRevision)
                .filter(Objects::nonNull).mapToLong(Integer::longValue).max().orElse(1L);
        List<IndexOverviewCalculator.IndexInput> inputs = rows.stream()
                .map(this::toInput)
                .toList();
        IndexOverview indices = calculator.calculate(new IndexOverviewCalculator.SnapshotInput(
                tradingDate, asOf, session, freshnessStatus, revision, source, inputs));
        BreadthService.Snapshot breadth = breadthService.latestFor(tradingDate).orElse(null);
        RegimeAssessmentService.Snapshot regime = regimeAssessmentService.latestFor(tradingDate).orElse(null);
        DataStatus overall = breadth == null ? overallStatus(indices.indices())
                : DataStatus.mostActionable(overallStatus(indices.indices()), breadth.dataStatus());
        if (regime != null) overall = DataStatus.mostActionable(overall, regime.assessment().dataStatus());
        return new MarketOverview(generatedAt, indices, breadth, regime, overall, etagToken(indices));
    }

    public static MarketOverview empty(Instant generatedAt) {
        LocalDate tradingDate = generatedAt.atZone(MARKET_ZONE).toLocalDate();
        IndexOverview indices = new IndexOverviewCalculator().calculate(new IndexOverviewCalculator.SnapshotInput(
                tradingDate, generatedAt, SessionState.UNKNOWN, DataStatus.UNAVAILABLE, 1,
                "UNAVAILABLE", List.of()));
        return new MarketOverview(generatedAt, indices, null, null, DataStatus.UNAVAILABLE, "no-accepted-index-data");
    }

    private IndexOverviewCalculator.IndexInput toInput(MarketOverviewRepository.LatestIndexSnapshot row) {
        return new IndexOverviewCalculator.IndexInput(IndexCode.valueOf(row.getIndexCode()),
                row.getIndexLevel(), row.getReferenceLevel(), row.getMatchedVolume(),
                row.getMatchedValueVnd(), List.of());
    }

    private static SessionState commonSession(List<MarketOverviewRepository.LatestIndexSnapshot> rows) {
        SessionState first = SessionState.valueOf(rows.getFirst().getSessionState());
        return rows.stream().allMatch(row -> first.name().equals(row.getSessionState()))
                ? first : SessionState.UNKNOWN;
    }

    private static String commonSource(List<MarketOverviewRepository.LatestIndexSnapshot> rows) {
        String first = rows.getFirst().getSource();
        return rows.stream().allMatch(row -> first.equals(row.getSource())) ? first : "MULTIPLE_ACCEPTED_SOURCES";
    }

    private static DataStatus overallStatus(List<IndexOverview.IndexFact> facts) {
        long available = facts.stream().filter(fact -> fact.dataStatus() != DataStatus.UNAVAILABLE).count();
        if (available == 0) {
            return DataStatus.UNAVAILABLE;
        }
        if (available != facts.size()) {
            return DataStatus.PARTIAL;
        }
        return facts.stream().map(IndexOverview.IndexFact::dataStatus)
                .reduce(DataStatus.CURRENT, DataStatus::mostActionable);
    }

    private static String etagToken(IndexOverview indices) {
        return "market-" + indices.tradingDate() + "-" + indices.observedAt().toEpochMilli()
                + "-r" + indices.revision();
    }

    public record MarketOverview(
            Instant generatedAt,
            IndexOverview indices,
            BreadthService.Snapshot breadth,
            RegimeAssessmentService.Snapshot regime,
            DataStatus dataStatus,
            String etag) {
    }
}
