package com.minhnb.finvera_be.portfolio.repository;

import com.minhnb.finvera_be.portfolio.entity.WatchlistItemEntity;
import com.minhnb.finvera_be.portfolio.entity.WatchlistItemId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItemEntity, WatchlistItemId> {

    List<WatchlistItemEntity> findByIdWatchlistIdOrderByAddedAtAsc(UUID watchlistId);

    int countByIdWatchlistId(UUID watchlistId);

    interface WatchlistItemCount {
        UUID getWatchlistId();
        int getItemCount();
    }

    @org.springframework.data.jpa.repository.Query(
            "SELECT w.id.watchlistId AS watchlistId, CAST(COUNT(w) AS int) AS itemCount "
                    + "FROM WatchlistItemEntity w WHERE w.id.watchlistId IN :watchlistIds GROUP BY w.id.watchlistId")
    List<WatchlistItemCount> countByWatchlistIds(@org.springframework.data.repository.query.Param("watchlistIds") java.util.Collection<UUID> watchlistIds);

    void deleteByIdWatchlistId(UUID watchlistId);

    Optional<WatchlistItemEntity> findByIdWatchlistIdAndIdInstrumentId(UUID watchlistId, UUID instrumentId);

    void deleteByIdWatchlistIdAndIdInstrumentId(UUID watchlistId, UUID instrumentId);

    boolean existsByIdWatchlistIdAndIdInstrumentId(UUID watchlistId, UUID instrumentId);
}
