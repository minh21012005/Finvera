package com.minhnb.finvera_be.backtest.repository;
import com.minhnb.finvera_be.backtest.entity.BacktestEquityPointEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BacktestEquityPointRepository extends JpaRepository<BacktestEquityPointEntity,UUID>{Page<BacktestEquityPointEntity> findAllByRunIdOrderByTradingDate(UUID runId,Pageable pageable);}
