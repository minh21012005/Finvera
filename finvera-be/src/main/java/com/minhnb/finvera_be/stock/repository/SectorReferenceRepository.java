package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.SectorReferenceEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectorReferenceRepository extends JpaRepository<SectorReferenceEntity, UUID> {

    Optional<SectorReferenceEntity> findBySchemeAndSchemeVersionAndSectorCode(
            String scheme, String schemeVersion, String sectorCode);
}
