package com.minhnb.finvera_be.portfolio.repository;

import com.minhnb.finvera_be.portfolio.entity.WatchlistEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistRepository extends JpaRepository<WatchlistEntity, UUID> {

    Optional<WatchlistEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<WatchlistEntity> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    boolean existsByOwnerIdAndName(UUID ownerId, String name);
}
