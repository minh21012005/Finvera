package com.minhnb.finvera_be.portfolio.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.portfolio.dto.AddWatchlistItemRequest;
import com.minhnb.finvera_be.portfolio.dto.CreateWatchlistRequest;
import com.minhnb.finvera_be.portfolio.dto.RenameWatchlistRequest;
import com.minhnb.finvera_be.portfolio.dto.WatchlistDetailResponse;
import com.minhnb.finvera_be.portfolio.dto.WatchlistItemResponse;
import com.minhnb.finvera_be.portfolio.dto.WatchlistSummaryResponse;
import com.minhnb.finvera_be.portfolio.entity.WatchlistEntity;
import com.minhnb.finvera_be.portfolio.entity.WatchlistItemEntity;
import com.minhnb.finvera_be.portfolio.entity.WatchlistItemId;
import com.minhnb.finvera_be.portfolio.repository.WatchlistItemRepository;
import com.minhnb.finvera_be.portfolio.repository.WatchlistRepository;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.DuplicateWatchlistNameException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.UnsupportedInstrumentException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.WatchlistNotFoundException;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorComponent;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.CandidateFacts;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.IndicatorSnapshot;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.TrendResult;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService.DailyBarReference;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService.EquityProfileReference;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService.SignalReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final MarketReferenceDataService marketReferenceData;
    private final StockReferenceDataService stockReferenceData;
    private final OwnerScopedAccess ownerScopedAccess;
    private final Clock clock;

    public WatchlistService(
            WatchlistRepository watchlistRepository,
            WatchlistItemRepository watchlistItemRepository,
            MarketReferenceDataService marketReferenceData,
            StockReferenceDataService stockReferenceData,
            OwnerScopedAccess ownerScopedAccess,
            Clock clock) {
        this.watchlistRepository = watchlistRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.marketReferenceData = marketReferenceData;
        this.stockReferenceData = stockReferenceData;
        this.ownerScopedAccess = ownerScopedAccess;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WatchlistSummaryResponse> listWatchlists() {
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();
        List<WatchlistEntity> entities = watchlistRepository.findByOwnerIdOrderByCreatedAtAsc(ownerId);
        if (entities.isEmpty()) {
            return List.of();
        }

        List<UUID> watchlistIds = entities.stream().map(WatchlistEntity::getId).toList();
        Map<UUID, Integer> counts = watchlistItemRepository.countByWatchlistIds(watchlistIds).stream()
                .collect(Collectors.toMap(
                        WatchlistItemRepository.WatchlistItemCount::getWatchlistId,
                        WatchlistItemRepository.WatchlistItemCount::getItemCount));

        return entities.stream()
                .map(w -> new WatchlistSummaryResponse(
                        w.getId(),
                        w.getName(),
                        w.getCreatedAt(),
                        counts.getOrDefault(w.getId(), 0)))
                .toList();
    }

    @Transactional
    public WatchlistSummaryResponse createWatchlist(CreateWatchlistRequest request) {
        Objects.requireNonNull(request, "request");
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();

        String name = request.name().trim();
        if (watchlistRepository.existsByOwnerIdAndName(ownerId, name)) {
            throw new DuplicateWatchlistNameException(name);
        }

        UUID watchlistId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        WatchlistEntity entity = new WatchlistEntity(watchlistId, ownerId, name, now);
        watchlistRepository.save(entity);

        return new WatchlistSummaryResponse(watchlistId, name, now, 0);
    }

    @Transactional(readOnly = true)
    public WatchlistDetailResponse getWatchlist(UUID watchlistId) {
        Objects.requireNonNull(watchlistId, "watchlistId");
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();

        WatchlistEntity entity = watchlistRepository.findByIdAndOwnerId(watchlistId, ownerId)
                .orElseThrow(() -> new WatchlistNotFoundException(watchlistId));

        List<WatchlistItemEntity> items = watchlistItemRepository.findByIdWatchlistIdOrderByAddedAtAsc(watchlistId);

        Set<UUID> instrumentIds = items.stream()
                .map(WatchlistItemEntity::getInstrumentId)
                .collect(Collectors.toSet());

        Map<UUID, InstrumentReference> instruments = new HashMap<>();
        Map<UUID, EquityProfileReference> profiles = new HashMap<>();
        Map<UUID, DailyBarReference> latestBars = new HashMap<>();
        Map<UUID, SignalReference> signals = new HashMap<>();
        Map<UUID, Map<IndicatorCode, IndicatorSnapshot>> indicatorsByInstrument = Map.of();

        if (!instrumentIds.isEmpty()) {
            for (InstrumentReference inst : marketReferenceData.findInstrumentsByIds(instrumentIds)) {
                instruments.put(inst.instrumentId(), inst);
            }
            for (EquityProfileReference prof : stockReferenceData.findEquityProfiles(instrumentIds)) {
                profiles.put(prof.instrumentId(), prof);
            }
            for (DailyBarReference bar : stockReferenceData.findLatestDailyBars(instrumentIds)) {
                latestBars.put(bar.instrumentId(), bar);
            }
            for (SignalReference sig : stockReferenceData.findCurrentSignalsForInstruments(instrumentIds)) {
                signals.put(sig.instrumentId(), sig);
            }
            indicatorsByInstrument = stockReferenceData.findLatestTechnicalIndicators(instrumentIds);
        }

        List<WatchlistItemResponse> itemResponses = new ArrayList<>();
        List<String> coherenceParts = new ArrayList<>();

        for (WatchlistItemEntity item : items) {
            UUID instId = item.getInstrumentId();
            coherenceParts.add(instId.toString());

            InstrumentReference inst = instruments.get(instId);
            String symbol = inst != null ? inst.symbol() : "UNKNOWN";

            EquityProfileReference prof = profiles.get(instId);
            String companyName = prof != null
                    ? (prof.companyNameVi() != null ? prof.companyNameVi() : prof.companyNameEn())
                    : symbol;

            DailyBarReference bar = latestBars.get(instId);
            SignalReference sig = signals.get(instId);

            String currentPrice = null;
            String dailyChangePercent = null;
            String technicalTrend = null;
            String volumeCondition = null;
            String dataStatus = "UNAVAILABLE";
            String reasonCode = null;

            if (bar != null) {
                currentPrice = PositionService.formatDecimal(bar.closePrice());
                dataStatus = "CURRENT";
                coherenceParts.add(bar.id().toString());

                // If open price is available, compute approximate daily change
                if (bar.openPrice() != null && bar.openPrice().signum() > 0 && bar.closePrice() != null) {
                    BigDecimal changePct = bar.closePrice().subtract(bar.openPrice())
                            .divide(bar.openPrice(), 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    dailyChangePercent = PositionService.formatDecimal(changePct);
                }

                // Reuse Feature 002/003's own persisted technical-indicators-v1 snapshot
                // (research R-009/FR-009) — never recompute trend/volume condition here.
                Map<IndicatorCode, IndicatorSnapshot> indicators =
                        indicatorsByInstrument.getOrDefault(instId, Map.of());
                CandidateFacts trendFacts = new CandidateFacts(
                        instId, symbol, companyName, null, null, null, null, null, null,
                        null, null, null, null, indicators, null, false, null);
                TrendResult trend = ScreenerV1.deriveTrend(trendFacts);
                if (!trend.unavailable() && trend.direction() != null) {
                    technicalTrend = trend.direction().name();
                } else {
                    reasonCode = "INSUFFICIENT_HISTORY";
                }

                IndicatorSnapshot relativeVolume = indicators.get(IndicatorCode.RELATIVE_VOLUME);
                BigDecimal relativeVolumeRatio = relativeVolume != null
                        && relativeVolume.applicability() == MetricApplicability.DEFINED
                        ? relativeVolume.components().get(IndicatorComponent.VALUE)
                        : null;
                if (relativeVolumeRatio != null) {
                    if (relativeVolumeRatio.compareTo(BigDecimal.valueOf(1.5)) >= 0) {
                        volumeCondition = "HIGH";
                    } else if (relativeVolumeRatio.compareTo(BigDecimal.valueOf(0.5)) <= 0) {
                        volumeCondition = "LOW";
                    } else {
                        volumeCondition = "NORMAL";
                    }
                } else if (reasonCode == null) {
                    reasonCode = "INSUFFICIENT_HISTORY";
                }

                // Price is current, but trend/volume are not fully available —
                // PARTIAL, not CURRENT, so the item doesn't overstate completeness.
                if (reasonCode != null) {
                    dataStatus = "PARTIAL";
                }
            } else {
                reasonCode = "NO_BARS_AVAILABLE";
            }

            boolean hasSignal = sig != null;
            String signalDirection = hasSignal ? sig.direction() : null;
            String riskLevel = hasSignal ? sig.riskLevel() : null;

            itemResponses.add(new WatchlistItemResponse(
                    symbol,
                    companyName,
                    item.getAddedAt(),
                    currentPrice,
                    dailyChangePercent,
                    technicalTrend,
                    volumeCondition,
                    hasSignal,
                    signalDirection,
                    riskLevel,
                    dataStatus,
                    reasonCode));
        }

        String coherenceKey = PortfolioCoherenceKey.of(coherenceParts);
        Instant asOf = Instant.now(clock);

        return new WatchlistDetailResponse(
                entity.getId(),
                entity.getName(),
                itemResponses,
                coherenceKey,
                asOf);
    }

    @Transactional
    public WatchlistSummaryResponse renameWatchlist(UUID watchlistId, RenameWatchlistRequest request) {
        Objects.requireNonNull(watchlistId, "watchlistId");
        Objects.requireNonNull(request, "request");
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();

        WatchlistEntity entity = watchlistRepository.findByIdAndOwnerId(watchlistId, ownerId)
                .orElseThrow(() -> new WatchlistNotFoundException(watchlistId));

        String newName = request.name().trim();
        if (!entity.getName().equals(newName) && watchlistRepository.existsByOwnerIdAndName(ownerId, newName)) {
            throw new DuplicateWatchlistNameException(newName);
        }

        entity.setName(newName);
        watchlistRepository.save(entity);

        return new WatchlistSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getCreatedAt(),
                watchlistItemRepository.countByIdWatchlistId(entity.getId()));
    }

    @Transactional
    public void deleteWatchlist(UUID watchlistId) {
        Objects.requireNonNull(watchlistId, "watchlistId");
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();

        WatchlistEntity entity = watchlistRepository.findByIdAndOwnerId(watchlistId, ownerId)
                .orElseThrow(() -> new WatchlistNotFoundException(watchlistId));

        watchlistItemRepository.deleteByIdWatchlistId(watchlistId);
        watchlistRepository.delete(entity);
    }

    @Transactional
    public WatchlistDetailResponse addItem(UUID watchlistId, AddWatchlistItemRequest request) {
        Objects.requireNonNull(watchlistId, "watchlistId");
        Objects.requireNonNull(request, "request");
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();

        WatchlistEntity entity = watchlistRepository.findByIdAndOwnerId(watchlistId, ownerId)
                .orElseThrow(() -> new WatchlistNotFoundException(watchlistId));

        String symbol = request.symbol().trim().toUpperCase();
        InstrumentReference inst = marketReferenceData.findActiveInstrumentBySymbol(symbol)
                .orElseThrow(() -> new UnsupportedInstrumentException(request.symbol()));

        WatchlistItemId itemId = new WatchlistItemId(watchlistId, inst.instrumentId());
        if (!watchlistItemRepository.existsById(itemId)) {
            WatchlistItemEntity itemEntity = new WatchlistItemEntity(itemId, Instant.now(clock));
            watchlistItemRepository.save(itemEntity);
        }

        return getWatchlist(watchlistId);
    }

    @Transactional
    public void removeItem(UUID watchlistId, String symbol) {
        Objects.requireNonNull(watchlistId, "watchlistId");
        Objects.requireNonNull(symbol, "symbol");
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();

        watchlistRepository.findByIdAndOwnerId(watchlistId, ownerId)
                .orElseThrow(() -> new WatchlistNotFoundException(watchlistId));

        // Resolve regardless of active/delisted status (spec.md edge case: a
        // delisted watchlist symbol must remain removable), not just active
        // instruments — findActiveInstrumentBySymbol would silently no-op here.
        Optional<InstrumentReference> inst =
                marketReferenceData.findInstrumentBySymbolIncludingDelisted(symbol.trim().toUpperCase());
        if (inst.isPresent()) {
            WatchlistItemId itemId = new WatchlistItemId(watchlistId, inst.get().instrumentId());
            watchlistItemRepository.deleteById(itemId);
        }
    }
}
