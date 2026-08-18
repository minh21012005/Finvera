package com.minhnb.finvera_be.market.repository;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MarketInstrumentRepository extends JpaRepository<MarketInstrumentEntity, UUID> {
    Optional<MarketInstrumentEntity> findByVenueAndSymbolAndListedFrom(
            String venue, String symbol, LocalDate listedFrom);

    Optional<MarketInstrumentEntity> findFirstBySymbolAndListedToIsNull(String symbol);

    List<MarketInstrumentEntity> findByListedToIsNullAndSymbolStartingWithIgnoreCaseOrderBySymbolAsc(
            String symbolPrefix, Limit limit);
}
