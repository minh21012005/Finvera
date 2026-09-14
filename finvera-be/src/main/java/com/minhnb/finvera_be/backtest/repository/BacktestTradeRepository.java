package com.minhnb.finvera_be.backtest.repository;
import com.minhnb.finvera_be.backtest.entity.BacktestTradeEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BacktestTradeRepository extends JpaRepository<BacktestTradeEntity,UUID>{Page<BacktestTradeEntity> findAllByRunIdOrderBySequenceNo(UUID runId,Pageable pageable);}
