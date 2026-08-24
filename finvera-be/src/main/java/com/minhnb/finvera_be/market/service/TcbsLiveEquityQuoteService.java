package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.entity.EquityPriceObservationEntity;
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
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.transaction.annotation.Transactional;

/** Persists accepted TCBS matched-price ticks and exposes them through an application API. */
public class TcbsLiveEquityQuoteService implements LiveStockQuoteService,
        Consumer<TcbsThesisFrameMapper.Event> {
    private static final String SOURCE = "TCBS_IFLASH_THESIS";
    private static final String DATASET = "EQUITY_PRICE";
    private static final BigDecimal MAX_AVERAGE_PRICE_RATIO = new BigDecimal("50");
    private static final BigDecimal MIN_AVERAGE_PRICE_RATIO = new BigDecimal("0.02");
    private final MarketReferenceDataService referenceData;
    private final IngestionRecordService ingestionRecords;
    private final EquityPriceObservationRepository prices;
    private final TcbsThesisWebSocketClient client;
    private final Clock clock;
    private final Map<String, BigDecimal> referencePrices = new ConcurrentHashMap<>();
    private final Map<String, SessionFacts> sessionFacts = new ConcurrentHashMap<>();

    public TcbsLiveEquityQuoteService(MarketReferenceDataService referenceData,
            IngestionRecordService ingestionRecords, EquityPriceObservationRepository prices,
            TcbsThesisWebSocketClient client, Clock clock) {
        this.referenceData = referenceData;
        this.ingestionRecords = ingestionRecords;
        this.prices = prices;
        this.client = client;
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
        sessionFacts.put(trade.symbol(), new SessionFacts(trade.totalVolume(), trade.totalValueVnd()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LiveQuote> findLatest(String symbol) {
        var instrument = referenceData.findActiveInstrumentBySymbol(symbol.toUpperCase(Locale.ROOT));
        if (instrument.isEmpty()) return Optional.empty();
        return prices.findFirstByInstrumentIdOrderByObservedAtDesc(instrument.orElseThrow().instrumentId())
                .filter(row -> "TCBS_STREAM_RECEIVE_TIME".equals(row.getQualityReason()))
                .map(row -> {
                    SessionFacts facts = sessionFacts.getOrDefault(symbol.toUpperCase(Locale.ROOT), new SessionFacts(null, null));
                    return new LiveQuote(symbol.toUpperCase(Locale.ROOT), row.getMatchedOrClosePrice(),
                            row.getOfficialReferencePrice(), facts.volume(), facts.valueVnd(), row.getTradingDate(),
                            row.getObservedAt(), SOURCE);
                });
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

    private record SessionFacts(Long volume, BigDecimal valueVnd) { }
}
