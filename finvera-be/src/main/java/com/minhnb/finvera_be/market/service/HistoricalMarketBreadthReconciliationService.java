package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator;
import com.minhnb.finvera_be.market.domain.breadth.BreadthUniversePolicy;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.AdjustmentStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rebuilds end-of-day breadth from accepted stock daily bars after the local
 * Vnstock/KBS refresh has imported completed-session data.
 */
@Service
@ConditionalOnBean(StockReferenceDataService.class)
public class HistoricalMarketBreadthReconciliationService {
    private final MarketInstrumentRepository instruments;
    private final StockReferenceDataService stockReferenceData;
    private final BreadthService breadth;
    private final LiveMarketRegimeReconciliationService regimes;
    private final BreadthCalculator calculator = new BreadthCalculator(new BreadthUniversePolicy());

    public HistoricalMarketBreadthReconciliationService(MarketInstrumentRepository instruments,
            StockReferenceDataService stockReferenceData, BreadthService breadth,
            LiveMarketRegimeReconciliationService regimes) {
        this.instruments = instruments;
        this.stockReferenceData = stockReferenceData;
        this.breadth = breadth;
        this.regimes = regimes;
    }

    @Transactional
    public Result reconcileLatestCompletedSession() {
        List<MarketInstrumentEntity> universe = instruments
                .findByListedToIsNullAndInstrumentTypeAndStatusOrderByVenueAscSymbolAsc("COMMON_EQUITY", "ACTIVE");
        if (universe.isEmpty()) {
            return Result.skipped("NO_ACTIVE_COMMON_EQUITY_UNIVERSE");
        }

        List<UUID> instrumentIds = universe.stream().map(MarketInstrumentEntity::getId).toList();
        Map<UUID, List<StockReferenceDataService.DailyBarReference>> barsByInstrument = stockReferenceData
                .findLatestDailyBars(instrumentIds, 2)
                .stream()
                .collect(Collectors.groupingBy(StockReferenceDataService.DailyBarReference::instrumentId,
                        LinkedHashMap::new, Collectors.toList()));
        List<StockReferenceDataService.DailyBarReference> currentBars = barsByInstrument.values().stream()
                .filter(bars -> !bars.isEmpty())
                .map(List::getLast)
                .toList();
        if (currentBars.isEmpty()) {
            return Result.skipped("NO_DAILY_BAR_HISTORY");
        }

        LocalDate tradingDate = currentBars.stream()
                .map(StockReferenceDataService.DailyBarReference::tradingDate)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        Instant asOf = currentBars.stream()
                .filter(bar -> tradingDate.equals(bar.tradingDate()))
                .map(StockReferenceDataService.DailyBarReference::acceptedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
        Build build = build(universe, barsByInstrument, tradingDate);
        if (build.inputs().stream().noneMatch(input -> input.matchedOrClosePrice() != null)) {
            return Result.skipped("NO_DAILY_BAR_HISTORY_FOR_LATEST_SESSION");
        }

        BreadthCalculator.Result calculated = withAdditionalReasons(calculator.calculate(build.inputs()), build.reasonCodes());
        String universeHash = universeHash(tradingDate, build.inputs(), build.links());
        BreadthService.Snapshot snapshot = breadth.latestFor(tradingDate)
                .filter(existing -> existing.asOf().equals(asOf) && existing.universeHash().equals(universeHash))
                .orElseGet(() -> breadth.persist(tradingDate, asOf, universeHash, calculated, build.links(), "EOD"));
        regimes.reconcileEndOfDayIfMissingOrOlder(tradingDate, snapshot);
        return new Result("APPLIED", tradingDate, snapshot.id(), calculated.eligible(), calculated.advancing(),
                calculated.declining(), calculated.unchanged(), calculated.unclassified(), null);
    }

    private static Build build(List<MarketInstrumentEntity> universe,
            Map<UUID, List<StockReferenceDataService.DailyBarReference>> barsByInstrument,
            LocalDate tradingDate) {
        List<BreadthCalculator.SecurityInput> inputs = new ArrayList<>();
        List<BreadthService.InputLink> links = new ArrayList<>();
        for (MarketInstrumentEntity instrument : universe) {
            List<StockReferenceDataService.DailyBarReference> bars = barsByInstrument
                    .getOrDefault(instrument.getId(), List.of());
            StockReferenceDataService.DailyBarReference current = bars.stream()
                    .filter(bar -> tradingDate.equals(bar.tradingDate()))
                    .max(Comparator.comparing(StockReferenceDataService.DailyBarReference::acceptedAt))
                    .orElse(null);
            StockReferenceDataService.DailyBarReference previous = bars.stream()
                    .filter(bar -> bar.tradingDate().isBefore(tradingDate))
                    .max(Comparator.comparing(StockReferenceDataService.DailyBarReference::tradingDate))
                    .orElse(null);
            BigDecimal currentClose = current == null ? null : current.closePrice();
            BigDecimal officialReference = current == null ? null : current.referencePrice();
            BigDecimal previousClose = previous == null ? null : previous.closePrice();
            BigDecimal referencePrice = officialReference != null ? officialReference : previousClose;
            inputs.add(new BreadthCalculator.SecurityInput(Venue.valueOf(instrument.getVenue()),
                    instrument.getSymbol(), instrument.getIsin(), true, false,
                    BreadthUniversePolicy.InstrumentType.COMMON_EQUITY, currentClose, referencePrice,
                    AdjustmentStatus.RAW));
            links.add(new BreadthService.InputLink(instrument.getId(), current == null ? null : current.id(),
                    classification(currentClose, referencePrice), reasonCode(currentClose, officialReference, previousClose)));
        }
        List<String> reasons = links.stream().map(BreadthService.InputLink::reasonCode)
                .filter(Objects::nonNull).distinct().toList();
        return new Build(inputs, links, reasons);
    }

    private static String classification(BigDecimal currentClose, BigDecimal previousClose) {
        if (currentClose == null || previousClose == null) return "UNCLASSIFIED";
        int comparison = currentClose.compareTo(previousClose);
        if (comparison > 0) return "ADVANCING";
        if (comparison < 0) return "DECLINING";
        return "UNCHANGED";
    }

    private static String reasonCode(BigDecimal currentClose, BigDecimal officialReference, BigDecimal previousClose) {
        if (currentClose == null) return "MISSING_PRICE";
        if (officialReference != null) return null;
        if (previousClose == null) return "MISSING_REFERENCE_PRICE";
        return "REFERENCE_PRICE_UNAVAILABLE_USING_PRIOR_CLOSE";
    }

    private static BreadthCalculator.Result withAdditionalReasons(BreadthCalculator.Result result,
            List<String> additionalReasons) {
        List<String> reasons = new ArrayList<>(result.reasonCodes());
        for (String reason : additionalReasons) {
            if (!reasons.contains(reason)) {
                reasons.add(reason);
            }
        }
        return new BreadthCalculator.Result(result.advancing(), result.declining(), result.unchanged(),
                result.unclassified(), result.eligible(), reasons);
    }

    private static String universeHash(LocalDate tradingDate, List<BreadthCalculator.SecurityInput> inputs,
            List<BreadthService.InputLink> links) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(("historical-eod-breadth-v1|" + tradingDate + "\n").getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < inputs.size(); i++) {
                BreadthCalculator.SecurityInput input = inputs.get(i);
                BreadthService.InputLink link = links.get(i);
                digest.update((input.venue() + "|" + input.symbol() + "|" + nullToEmpty(input.isin()) + "|"
                        + value(input.matchedOrClosePrice()) + "|" + value(input.officialReferencePrice()) + "|"
                        + link.priceObservationId() + "|" + link.classification() + "|"
                        + nullToEmpty(link.reasonCode()) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String value(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Build(List<BreadthCalculator.SecurityInput> inputs, List<BreadthService.InputLink> links,
                         List<String> reasonCodes) { }

    public record Result(String status, LocalDate tradingDate, UUID breadthSnapshotId, int eligible, int advancing,
                         int declining, int unchanged, int unclassified, String reasonCode) {
        private static Result skipped(String reasonCode) {
            return new Result("SKIPPED", null, null, 0, 0, 0, 0, 0, reasonCode);
        }
    }
}
