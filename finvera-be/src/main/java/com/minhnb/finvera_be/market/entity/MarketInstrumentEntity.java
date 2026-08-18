package com.minhnb.finvera_be.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "market_instrument")
public class MarketInstrumentEntity {
    @Id private UUID id;
    private String isin;
    private String venue;
    private String symbol;
    @Column(name = "instrument_type") private String instrumentType;
    @Column(name = "listed_from") private LocalDate listedFrom;
    @Column(name = "listed_to") private LocalDate listedTo;
    private String status;
    private String source;
    @Column(name = "source_revision") private String sourceRevision;
    protected MarketInstrumentEntity() { }
    public MarketInstrumentEntity(UUID id, String isin, String venue, String symbol, String instrumentType,
            LocalDate listedFrom, LocalDate listedTo, String status, String source, String sourceRevision) {
        this.id=id; this.isin=isin; this.venue=venue; this.symbol=symbol; this.instrumentType=instrumentType;
        this.listedFrom=listedFrom; this.listedTo=listedTo; this.status=status; this.source=source;
        this.sourceRevision=sourceRevision;
    }
    public UUID getId() { return id; }
    public String getVenue() { return venue; }
    public String getSymbol() { return symbol; }
    public String getInstrumentType() { return instrumentType; }
    public String getStatus() { return status; }
}
