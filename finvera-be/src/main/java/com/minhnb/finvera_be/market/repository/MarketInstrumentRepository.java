package com.minhnb.finvera_be.market.repository;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MarketInstrumentRepository extends JpaRepository<MarketInstrumentEntity, UUID> {
    Optional<MarketInstrumentEntity> findByVenueAndSymbolAndListedFrom(
            String venue, String symbol, LocalDate listedFrom);
}
