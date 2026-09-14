package com.minhnb.finvera_be.backtest.repository;
import com.minhnb.finvera_be.backtest.entity.BacktestResultEntities.Evidence;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BacktestEvidenceRepository extends JpaRepository<Evidence,UUID>{List<Evidence> findAllByRunIdOrderByKey(UUID runId);}
