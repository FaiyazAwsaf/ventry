package com.ventry.event.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    @Column(nullable = false)
    private String venue;

    private String bannerUrl;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketTier> tiers = new ArrayList<>();

    protected Event() {
    }

    public Event(String name, String description, LocalDateTime eventDate, String venue, String bannerUrl) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.eventDate = eventDate;
        this.venue = venue;
        this.bannerUrl = bannerUrl;
    }

    public void addTier(TicketTier tier) {
        tiers.add(tier);
        tier.setEvent(this);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public String getVenue() {
        return venue;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public List<TicketTier> getTiers() {
        return tiers;
    }
}
