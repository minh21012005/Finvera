package com.minhnb.finvera_be.backtest.repository;
import com.minhnb.finvera_be.backtest.entity.BacktestResultEntities.EntryEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BacktestEntryEventRepository extends JpaRepository<EntryEvent,UUID>{Page<EntryEvent> findAllByRunIdOrderBySequenceNo(UUID runId,Pageable pageable);}
