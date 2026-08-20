package com.minhnb.finvera_be.analyst.repository;

import com.minhnb.finvera_be.analyst.entity.AnalystToolCallEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalystToolCallRepository extends JpaRepository<AnalystToolCallEntity, UUID> {
    List<AnalystToolCallEntity> findByAnalystQueryIdOrderBySequenceNoAsc(UUID analystQueryId);
}
