package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketIndexEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketIndexRepository extends JpaRepository<MarketIndexEntity, UUID> {

    Optional<MarketIndexEntity> findByCode(String code);
}
