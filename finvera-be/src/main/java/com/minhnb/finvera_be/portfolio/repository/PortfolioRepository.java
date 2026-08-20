package com.minhnb.finvera_be.portfolio.repository;

import com.minhnb.finvera_be.portfolio.entity.PortfolioEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioRepository extends JpaRepository<PortfolioEntity, UUID> {

    Optional<PortfolioEntity> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);

    /**
     * Same lookup as {@link #findByIdAndOwnerIdAndDeletedAtIsNull}, but takes a
     * {@code SELECT ... FOR UPDATE} row lock held for the caller's whole
     * {@code @Transactional} method — serializes concurrent ledger-writing
     * requests (recordTransaction/voidTransaction) against the same portfolio
     * so two racing validations can no longer both read the same pre-write
     * snapshot and both pass (e.g. two concurrent SELLs oversell together).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PortfolioEntity p where p.id = :id and p.ownerId = :ownerId and p.deletedAt is null")
    Optional<PortfolioEntity> findByIdAndOwnerIdAndDeletedAtIsNullForUpdate(
            @Param("id") UUID id, @Param("ownerId") UUID ownerId);

    Optional<PortfolioEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<PortfolioEntity> findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID ownerId);

    boolean existsByOwnerIdAndNameAndDeletedAtIsNull(UUID ownerId, String name);

    boolean existsByOwnerIdAndName(UUID ownerId, String name);
}
