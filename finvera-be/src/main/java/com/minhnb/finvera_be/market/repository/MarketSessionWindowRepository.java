package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketSessionWindowEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketSessionWindowRepository extends JpaRepository<MarketSessionWindowEntity, UUID> {

    List<MarketSessionWindowEntity> findByVenue(String venue);
}
