package com.ventry.event.service;

import com.ventry.event.dto.CreateEventRequest;
import com.ventry.event.dto.CreateTierRequest;
import com.ventry.event.dto.EventAnalyticsResponse;
import com.ventry.event.dto.EventResponse;
import com.ventry.event.dto.TierAnalyticsResponse;
import com.ventry.event.dto.TierAvailabilityResponse;
import com.ventry.event.dto.TierResponse;
import com.ventry.event.dto.UpdateEventRequest;
import com.ventry.event.entity.Event;
import com.ventry.event.entity.TicketTier;
import com.ventry.event.exception.EventHasActiveBookingsException;
import com.ventry.event.exception.EventNotFoundException;
import com.ventry.event.exception.InsufficientInventoryException;
import com.ventry.event.exception.TierNotFoundException;
import com.ventry.event.repository.EventRepository;
import com.ventry.event.repository.TicketTierRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final TicketTierRepository ticketTierRepository;

    public EventService(EventRepository eventRepository, TicketTierRepository ticketTierRepository) {
        this.eventRepository = eventRepository;
        this.ticketTierRepository = ticketTierRepository;
    }

    @CacheEvict(value = "events", allEntries = true)
    public EventResponse createEvent(CreateEventRequest request) {
        Event event = new Event(
                request.name(),
                request.description(),
                request.eventDate(),
                request.venue(),
                request.bannerUrl()
        );
        for (CreateTierRequest tierRequest : request.tiers()) {
            event.addTier(new TicketTier(tierRequest.name(), tierRequest.price(), tierRequest.capacity()));
        }

        eventRepository.save(event);
        return toResponse(event);
    }

    @Cacheable("events")
    public List<EventResponse> browseEvents() {
        return eventRepository.findAll().stream().map(this::toResponse).toList();
    }

    public EventResponse getEvent(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return toResponse(event);
    }

    @CacheEvict(value = "events", allEntries = true)
    public EventResponse updateEvent(String eventId, UpdateEventRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        event.update(request.name(), request.description(), request.eventDate(), request.venue(), request.bannerUrl());
        eventRepository.save(event);
        return toResponse(event);
    }

    @CacheEvict(value = "events", allEntries = true)
    public void deleteEvent(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        boolean hasActiveBookings = event.getTiers().stream()
                .anyMatch(tier -> tier.getAvailable() < tier.getCapacity());
        if (hasActiveBookings) {
            throw new EventHasActiveBookingsException(eventId);
        }
        eventRepository.delete(event);
    }

    public TierAvailabilityResponse checkAvailability(String eventId, String tierId, int quantity) {
        TicketTier tier = ticketTierRepository.findByIdAndEventId(tierId, eventId)
                .orElseThrow(() -> new TierNotFoundException(eventId, tierId));
        return new TierAvailabilityResponse(tier.getAvailable() >= quantity, tier.getAvailable());
    }

    public EventAnalyticsResponse getEventAnalytics(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        List<TierAnalyticsResponse> tierAnalytics = event.getTiers().stream()
                .map(tier -> {
                    int reserved = tier.getCapacity() - tier.getAvailable();
                    BigDecimal revenue = tier.getPrice().multiply(BigDecimal.valueOf(reserved));
                    return new TierAnalyticsResponse(
                            tier.getId(), tier.getName(), tier.getCapacity(), tier.getAvailable(), reserved, revenue);
                })
                .toList();

        int totalCapacity = tierAnalytics.stream().mapToInt(TierAnalyticsResponse::capacity).sum();
        int totalReserved = tierAnalytics.stream().mapToInt(TierAnalyticsResponse::reserved).sum();
        BigDecimal totalRevenue = tierAnalytics.stream()
                .map(TierAnalyticsResponse::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new EventAnalyticsResponse(
                event.getId(), event.getName(), totalCapacity, totalReserved, totalRevenue, tierAnalytics);
    }

    // Custom @Modifying repository queries, unlike Spring Data's own built-in save()/deleteById(),
    // aren't auto-wrapped in a transaction - they need one supplied by the caller, hence @Transactional here.
    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public void reserveInventory(String eventId, String tierId, int quantity) {
        int updated = ticketTierRepository.reserve(tierId, eventId, quantity);
        if (updated == 0) {
            if (!ticketTierRepository.existsByIdAndEventId(tierId, eventId)) {
                throw new TierNotFoundException(eventId, tierId);
            }
            throw new InsufficientInventoryException(eventId, tierId, quantity);
        }
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public void releaseInventory(String eventId, String tierId, int quantity) {
        int updated = ticketTierRepository.release(tierId, eventId, quantity);
        // updated == 0 here without a missing tier just means the release would've overshot
        // capacity - treated as a no-op so a redelivered Kafka compensating-transaction message
        // doesn't throw, since duplicate release is safe by nature (unlike duplicate reserve).
        if (updated == 0 && !ticketTierRepository.existsByIdAndEventId(tierId, eventId)) {
            throw new TierNotFoundException(eventId, tierId);
        }
    }

    private EventResponse toResponse(Event event) {
        List<TierResponse> tiers = event.getTiers().stream()
                .map(tier -> new TierResponse(
                        tier.getId(),
                        tier.getName(),
                        tier.getPrice(),
                        tier.getCapacity(),
                        tier.getAvailable()
                ))
                .toList();

        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getEventDate(),
                event.getVenue(),
                event.getBannerUrl(),
                tiers
        );
    }
}
