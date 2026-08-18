package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketCalendarDayEntity;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketCalendarDayRepository extends JpaRepository<MarketCalendarDayEntity, UUID> {

    Optional<MarketCalendarDayEntity> findFirstByVenueAndTradingDateOrderByAcceptedAtDesc(
            String venue, LocalDate tradingDate);
}
