package com.ventry.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ticket_tiers")
public class TicketTier {

    @Id
    private String id;

    @Column(nullable = false)
    private String name; // e.g. Gold, Silver, Bronze

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int available;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    protected TicketTier() {
    }

    public TicketTier(String name, BigDecimal price, int capacity) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.price = price;
        this.capacity = capacity;
        this.available = capacity;
    }

    void setEvent(Event event) {
        this.event = event;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getAvailable() {
        return available;
    }

    public Event getEvent() {
        return event;
    }
}
