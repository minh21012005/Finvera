package com.minhnb.finvera_be.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "market_index")
public class MarketIndexEntity {

    @Id
    private UUID id;
    private String code;
    @Column(name = "provider_symbol")
    private String providerSymbol;
    @Column(name = "display_name")
    private String displayName;
    private String venue;
    @Column(name = "active_from")
    private LocalDate activeFrom;
    @Column(name = "active_to")
    private LocalDate activeTo;

    protected MarketIndexEntity() {
    }

    public MarketIndexEntity(UUID id, String code, String providerSymbol, String displayName,
            String venue, LocalDate activeFrom, LocalDate activeTo) {
        this.id = id;
        this.code = code;
        this.providerSymbol = providerSymbol;
        this.displayName = displayName;
        this.venue = venue;
        this.activeFrom = activeFrom;
        this.activeTo = activeTo;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }
}
