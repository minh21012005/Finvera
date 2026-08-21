package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquityProfileRepository extends JpaRepository<EquityProfileEntity, UUID> {

    Optional<EquityProfileEntity> findFirstByInstrumentIdAndEffectiveToIsNull(UUID instrumentId);

    List<EquityProfileEntity> findByInstrumentIdInAndEffectiveToIsNull(java.util.Collection<UUID> instrumentIds);

    /** Feature 003 research R-007: the screener's candidate universe root. */
    List<EquityProfileEntity> findByEffectiveToIsNullAndListingStatus(String listingStatus);

    List<EquityProfileEntity> findByEffectiveToIsNullAndSectorReferenceId(UUID sectorReferenceId);

    /**
     * Backfills {@code sector_reference_id} on the current profile row in place. Sector
     * classification is reference/dimension data (research.md R-012 G-04), not the kind of
     * point-in-time fact the {@code effective_from}/{@code effective_to} revision chain exists to
     * protect, so this updates rather than superseding — consistent with how this column already
     * starts nullable and unpopulated for every fixture-seeded profile.
     */
    @Modifying
    @Query("update EquityProfileEntity p set p.sectorReferenceId = :sectorReferenceId "
            + "where p.instrumentId = :instrumentId and p.effectiveTo is null")
    int updateSectorReferenceId(
            @Param("instrumentId") UUID instrumentId, @Param("sectorReferenceId") UUID sectorReferenceId);
}
