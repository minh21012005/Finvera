package com.minhnb.finvera_be.market.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.minhnb.finvera_be.market.entity.EquityPriceObservationEntity;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsHttpSessionState;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsThesisFrameMapper;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsThesisWebSocketClient;
import com.minhnb.finvera_be.market.repository.EquityPriceObservationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/** Persists accepted TCBS matched-price ticks and exposes them through an application API. */
public class TcbsLiveEquityQuoteService implements LiveStockQuoteService,
        Consumer<TcbsThesisFrameMapper.Event> {
    private static final Logger log = LoggerFactory.getLogger(TcbsLiveEquityQuoteService.class);
    private static final String SOURCE = "TCBS_IFLASH_THESIS";
    private static final String DATASET = "EQUITY_PRICE";
    private static final BigDecimal MAX_AVERAGE_PRICE_RATIO = new BigDecimal("50");
    private static final BigDecimal MIN_AVERAGE_PRICE_RATIO = new BigDecimal("0.02");
    private final MarketReferenceDataService referenceData;
    private final IngestionRecordService ingestionRecords;
    private final EquityPriceObservationRepository prices;
    private final TcbsThesisWebSocketClient client;
    private final Optional<TcbsHttpSessionState> sessionState;
    private final Clock clock;
    private final Map<String, BigDecimal> referencePrices = new ConcurrentHashMap<>();
    private final Map<String, SessionFacts> sessionFacts = new ConcurrentHashMap<>();

    public TcbsLiveEquityQuoteService(MarketReferenceDataService referenceData,
            IngestionRecordService ingestionRecords, EquityPriceObservationRepository prices,
            TcbsThesisWebSocketClient client, Clock clock) {
        this(referenceData, ingestionRecords, prices, client, Optional.empty(), clock);
    }

    public TcbsLiveEquityQuoteService(MarketReferenceDataService referenceData,
            IngestionRecordService ingestionRecords, EquityPriceObservationRepository prices,
            TcbsThesisWebSocketClient client, Optional<TcbsHttpSessionState> sessionState, Clock clock) {
        this.referenceData = referenceData;
        this.ingestionRecords = ingestionRecords;
        this.prices = prices;
        this.client = client;
        this.sessionState = Objects.requireNonNull(sessionState);
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public void ensureSubscribed(String requestedSymbol) {
        if (requestedSymbol == null) throw new IllegalArgumentException("symbol is required");
        String symbol = requestedSymbol.trim().toUpperCase(Locale.ROOT);
        if (referenceData.findActiveInstrumentBySymbol(symbol).isEmpty()) {
            throw new IllegalArgumentException("active symbol is required");
        }
        client.ensureEquitySubscribed(symbol);
        fetchSnapshotIfMissing(symbol);
    }

    @Override
    @Transactional
    public void accept(TcbsThesisFrameMapper.Event event) {
        if (event instanceof TcbsThesisFrameMapper.EquityReferenceUpdate reference) {
            referencePrices.put(reference.symbol(), reference.referencePrice());
            return;
        }
        if (!(event instanceof TcbsThesisFrameMapper.EquityTradeUpdate trade)) return;
        BigDecimal reference = referencePrices.get(trade.symbol());
        if (reference == null && trade.absoluteChange() != null) {
            BigDecimal derived = trade.matchPrice().subtract(trade.absoluteChange());
            if (derived.signum() > 0) reference = derived;
        }
        if (reference == null) return;
        if (hasImplausibleValueScale(trade)) return;
        var instrument = referenceData.findActiveInstrumentBySymbol(trade.symbol()).orElse(null);
        if (instrument == null) return;
        Instant observedAt = bucket(trade.receivedAt());
        var session = referenceData.resolveSession(instrument.venue(), trade.receivedAt());
        String hash = hash(trade.symbol(), observedAt, trade.matchPrice(), reference,
                trade.totalVolume(), trade.totalValueVnd());
        if (ingestionRecords.isDuplicate(SOURCE, DATASET, trade.symbol(), session.tradingDate(), observedAt, hash)) return;
        var latest = ingestionRecords.findLatestAccepted(SOURCE, DATASET, trade.symbol(), session.tradingDate());
        if (latest.isPresent() && latest.orElseThrow().observedAt().isAfter(observedAt)) return;
        UUID recordId = ingestionRecords.recordAccepted(SOURCE, DATASET, trade.symbol(), session.tradingDate(),
                observedAt, clock.instant(), hash, latest.map(IngestionRecordService.AcceptedRecord::id).orElse(null));
        prices.save(new EquityPriceObservationEntity(UUID.randomUUID(), instrument.instrumentId(), recordId,
                session.tradingDate(), observedAt, trade.matchPrice(), reference, null, null,
                "NOT_APPLICABLE", "TCBS_STREAM_RECEIVE_TIME"));
        final BigDecimal finalReference = reference;
        sessionFacts.compute(trade.symbol(), (sym, prev) -> {
            BigDecimal match = trade.matchPrice();
            boolean hasOfficial = prev != null && prev.hasOfficialSnapshot();
            BigDecimal open = hasOfficial ? prev.openPrice() : match;
            BigDecimal high = hasOfficial ? prev.highPrice().max(match) : match;
            BigDecimal low = hasOfficial ? prev.lowPrice().min(match) : match;
            return new SessionFacts(match, finalReference, open, high, low,
                    trade.totalVolume(), trade.totalValueVnd(), session.tradingDate(), hasOfficial);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LiveQuote> findLatest(String symbol) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        var instrument = referenceData.findActiveInstrumentBySymbol(sym);
        if (instrument.isEmpty()) return Optional.empty();

        var currentSession = referenceData.resolveSession(instrument.orElseThrow().venue(), clock.instant());
        SessionFacts facts = sessionFacts.get(sym);
        if (facts == null || !facts.hasOfficialSnapshot()) {
            fetchSnapshotIfMissing(sym);
            facts = sessionFacts.get(sym);
        }

        var latestObs = prices.findFirstByInstrumentIdOrderByObservedAtDesc(instrument.orElseThrow().instrumentId())
                .filter(row -> "TCBS_STREAM_RECEIVE_TIME".equals(row.getQualityReason()))
                .filter(row -> row.getTradingDate().equals(currentSession.tradingDate()));

        if (latestObs.isPresent()) {
            var row = latestObs.get();
            BigDecimal matchPrice = row.getMatchedOrClosePrice();
            BigDecimal refPrice = row.getOfficialReferencePrice();
            BigDecimal openPrice = facts != null && facts.openPrice() != null ? facts.openPrice() : refPrice;
            BigDecimal highPrice = facts != null && facts.highPrice() != null
                    ? (matchPrice != null ? facts.highPrice().max(matchPrice) : facts.highPrice())
                    : matchPrice;
            BigDecimal lowPrice = facts != null && facts.lowPrice() != null
                    ? (matchPrice != null ? facts.lowPrice().min(matchPrice) : facts.lowPrice())
                    : matchPrice;
            Long volume = facts != null ? facts.volume() : null;
            BigDecimal valueVnd = facts != null ? facts.valueVnd() : null;

            return Optional.of(new LiveQuote(sym, matchPrice, refPrice,
                    openPrice, highPrice, lowPrice,
                    volume, valueVnd, row.getTradingDate(),
                    row.getObservedAt(), SOURCE));
        }

        if (facts != null && facts.matchPrice() != null && facts.matchPrice().signum() > 0) {
            LocalDate tradeDate = facts.tradingDate() != null ? facts.tradingDate() : currentSession.tradingDate();
            BigDecimal matchPrice = facts.matchPrice();
            BigDecimal refPrice = facts.refPrice() != null ? facts.refPrice() : matchPrice;
            BigDecimal openPrice = facts.openPrice() != null ? facts.openPrice() : matchPrice;
            BigDecimal highPrice = facts.highPrice() != null ? facts.highPrice().max(matchPrice) : matchPrice;
            BigDecimal lowPrice = facts.lowPrice() != null ? facts.lowPrice().min(matchPrice) : matchPrice;

            return Optional.of(new LiveQuote(sym, matchPrice, refPrice,
                    openPrice, highPrice, lowPrice,
                    facts.volume(), facts.valueVnd(), tradeDate,
                    clock.instant(), SOURCE));
        }

        return Optional.empty();
    }

    private void fetchSnapshotIfMissing(String symbol) {
        if (sessionState.isEmpty() || !sessionState.get().isTokenPresent()) {
            log.warn("fetchSnapshotIfMissing skipped for {}: sessionState empty or token not present", symbol);
            return;
        }
        try {
            sessionState.get().getAuthenticated("/tartarus/v1/tickerCommons?tickers=" + symbol, TickerCommonsResponse.class)
                    .ifPresent(response -> {
                        if (response.data() == null || response.data().isEmpty()) {
                            log.warn("tickerCommons returned empty data for {}", symbol);
                            return;
                        }
                        var item = response.data().getFirst();
                        if (item == null) return;
                        LocalDate tradingDate = null;
                        if (response.tradingDate() != null && response.tradingDate().length() >= 10) {
                            try {
                                tradingDate = LocalDate.parse(response.tradingDate().substring(0, 10));
                            } catch (Exception ignored) { }
                        }
                        final LocalDate finalTradingDate = tradingDate;
                        sessionFacts.compute(symbol.toUpperCase(Locale.ROOT), (sym, existing) -> {
                            BigDecimal match = item.matchPrice() != null && item.matchPrice().signum() > 0
                                    ? item.matchPrice()
                                    : (existing != null ? existing.matchPrice() : null);
                            BigDecimal ref = item.refPrice() != null && item.refPrice().signum() > 0
                                    ? item.refPrice()
                                    : (existing != null ? existing.refPrice() : null);
                            BigDecimal open = item.open() != null && item.open().signum() > 0
                                    ? item.open()
                                    : (existing != null ? existing.openPrice() : null);
                            BigDecimal high = item.high() != null && item.high().signum() > 0
                                    ? item.high()
                                    : (existing != null ? existing.highPrice() : null);
                            BigDecimal low = item.low() != null && item.low().signum() > 0
                                    ? item.low()
                                    : (existing != null ? existing.lowPrice() : null);
                            if (existing != null && existing.matchPrice() != null) {
                                if (high != null) high = high.max(existing.matchPrice());
                                if (low != null) low = low.min(existing.matchPrice());
                            }
                            Long vol = item.totalVol() != null ? item.totalVol() : (existing != null ? existing.volume() : null);
                            BigDecimal val = item.totalVal() != null ? item.totalVal() : (existing != null ? existing.valueVnd() : null);
                            LocalDate date = finalTradingDate != null ? finalTradingDate : (existing != null ? existing.tradingDate() : null);
                            return new SessionFacts(match, ref, open, high, low, vol, val, date, true);
                        });
                        log.info("Successfully fetched tickerCommons snapshot for {}: open={}, high={}, low={}, match={}",
                                symbol, item.open(), item.high(), item.low(), item.matchPrice());
                    });
        } catch (Exception e) {
            log.warn("fetchSnapshotIfMissing failed for {}: {}", symbol, e.getMessage(), e);
        }
    }

    private static Instant bucket(Instant instant) {
        long epoch = instant.getEpochSecond();
        return Instant.ofEpochSecond(epoch - Math.floorMod(epoch, 30));
    }

    private static boolean hasImplausibleValueScale(TcbsThesisFrameMapper.EquityTradeUpdate trade) {
        if (trade.totalVolume() == null || trade.totalVolume() <= 0
                || trade.totalValueVnd() == null || trade.matchPrice() == null
                || trade.matchPrice().signum() <= 0) {
            return false;
        }
        BigDecimal averagePrice = trade.totalValueVnd()
                .divide(BigDecimal.valueOf(trade.totalVolume()), 12, RoundingMode.HALF_UP);
        BigDecimal ratio = averagePrice.divide(trade.matchPrice(), 12, RoundingMode.HALF_UP);
        return ratio.compareTo(MAX_AVERAGE_PRICE_RATIO) > 0 || ratio.compareTo(MIN_AVERAGE_PRICE_RATIO) < 0;
    }

    private static String hash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record SessionFacts(
            BigDecimal matchPrice,
            BigDecimal refPrice,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            Long volume,
            BigDecimal valueVnd,
            LocalDate tradingDate,
            boolean hasOfficialSnapshot) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TickerCommonsResponse(
            @JsonProperty("data") List<TickerCommonsItem> data,
            @JsonProperty("tradingDate") String tradingDate) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TickerCommonsItem(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("open") BigDecimal open,
            @JsonProperty("high") BigDecimal high,
            @JsonProperty("low") BigDecimal low,
            @JsonProperty("matchPrice") BigDecimal matchPrice,
            @JsonProperty("refPrice") BigDecimal refPrice,
            @JsonProperty("totalVol") Long totalVol,
            @JsonProperty("totalVal") BigDecimal totalVal) { }
}
