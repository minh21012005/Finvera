package com.minhnb.finvera_be.portfolio.controller;

import com.minhnb.finvera_be.portfolio.dto.AddWatchlistItemRequest;
import com.minhnb.finvera_be.portfolio.dto.CreateWatchlistRequest;
import com.minhnb.finvera_be.portfolio.dto.RenameWatchlistRequest;
import com.minhnb.finvera_be.portfolio.dto.WatchlistDetailResponse;
import com.minhnb.finvera_be.portfolio.dto.WatchlistSummaryResponse;
import com.minhnb.finvera_be.portfolio.service.WatchlistService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for Watchlist lifecycle and item tracking operations.
 * Path: {@code /api/v1/watchlists} per contracts/portfolio-watchlist.openapi.yaml.
 */
@RestController
@RequestMapping("/api/v1/watchlists")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistSummaryResponse> listWatchlists() {
        return watchlistService.listWatchlists();
    }

    @PostMapping
    public ResponseEntity<WatchlistSummaryResponse> createWatchlist(
            @Valid @RequestBody CreateWatchlistRequest request) {
        WatchlistSummaryResponse response = watchlistService.createWatchlist(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{watchlistId}")
    public WatchlistDetailResponse getWatchlist(@PathVariable UUID watchlistId) {
        return watchlistService.getWatchlist(watchlistId);
    }

    @PatchMapping("/{watchlistId}")
    public WatchlistSummaryResponse renameWatchlist(
            @PathVariable UUID watchlistId,
            @Valid @RequestBody RenameWatchlistRequest request) {
        return watchlistService.renameWatchlist(watchlistId, request);
    }

    @DeleteMapping("/{watchlistId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWatchlist(@PathVariable UUID watchlistId) {
        watchlistService.deleteWatchlist(watchlistId);
    }

    @PostMapping("/{watchlistId}/items")
    public WatchlistDetailResponse addItem(
            @PathVariable UUID watchlistId,
            @Valid @RequestBody AddWatchlistItemRequest request) {
        return watchlistService.addItem(watchlistId, request);
    }

    @DeleteMapping("/{watchlistId}/items/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(
            @PathVariable UUID watchlistId,
            @PathVariable String symbol) {
        watchlistService.removeItem(watchlistId, symbol);
    }
}
