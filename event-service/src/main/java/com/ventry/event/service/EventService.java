package com.ventry.event.service;

import com.ventry.event.dto.CreateEventRequest;
import com.ventry.event.dto.CreateTierRequest;
import com.ventry.event.dto.EventResponse;
import com.ventry.event.dto.TierResponse;
import com.ventry.event.entity.Event;
import com.ventry.event.entity.TicketTier;
import com.ventry.event.exception.EventNotFoundException;
import com.ventry.event.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

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

    public List<EventResponse> browseEvents() {
        return eventRepository.findAll().stream().map(this::toResponse).toList();
    }

    public EventResponse getEvent(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return toResponse(event);
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
