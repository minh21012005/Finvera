package com.minhnb.finvera_be.backtest.repository;
import com.minhnb.finvera_be.backtest.entity.BacktestResultEntities.Metric;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BacktestMetricRepository extends JpaRepository<Metric,UUID>{List<Metric> findAllByRunIdOrderByCode(UUID runId);}
