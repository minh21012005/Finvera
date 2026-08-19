package com.minhnb.finvera_be.portfolio.repository;

import com.minhnb.finvera_be.portfolio.entity.PortfolioEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<PortfolioEntity, UUID> {

    Optional<PortfolioEntity> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);

    Optional<PortfolioEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<PortfolioEntity> findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID ownerId);

    boolean existsByOwnerIdAndNameAndDeletedAtIsNull(UUID ownerId, String name);

    boolean existsByOwnerIdAndName(UUID ownerId, String name);
}
