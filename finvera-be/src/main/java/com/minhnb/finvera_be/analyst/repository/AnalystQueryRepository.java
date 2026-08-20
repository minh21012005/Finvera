package com.minhnb.finvera_be.analyst.repository;

import com.minhnb.finvera_be.analyst.entity.AnalystQueryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalystQueryRepository extends JpaRepository<AnalystQueryEntity, UUID> {
    List<AnalystQueryEntity> findByOwnerIdOrderByRequestedAtDesc(UUID ownerId);
}
