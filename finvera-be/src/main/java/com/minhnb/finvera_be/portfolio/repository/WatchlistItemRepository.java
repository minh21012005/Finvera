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

    void deleteByIdWatchlistId(UUID watchlistId);

    Optional<WatchlistItemEntity> findByIdWatchlistIdAndIdInstrumentId(UUID watchlistId, UUID instrumentId);

    void deleteByIdWatchlistIdAndIdInstrumentId(UUID watchlistId, UUID instrumentId);

    boolean existsByIdWatchlistIdAndIdInstrumentId(UUID watchlistId, UUID instrumentId);
}
