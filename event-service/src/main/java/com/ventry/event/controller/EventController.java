package com.ventry.event.controller;

import com.ventry.event.dto.CreateEventRequest;
import com.ventry.event.dto.EventResponse;
import com.ventry.event.dto.InventoryAdjustmentRequest;
import com.ventry.event.dto.TierAvailabilityResponse;
import com.ventry.event.dto.UpdateEventRequest;
import com.ventry.event.exception.ForbiddenException;
import com.ventry.event.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventResponse> browseEvents() {
        return eventService.browseEvents();
    }

    @GetMapping("/{eventId}")
    public EventResponse getEvent(@PathVariable String eventId) {
        return eventService.getEvent(eventId);
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody CreateEventRequest request
    ) {
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException(role);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(request));
    }

    @PutMapping("/{eventId}")
    public EventResponse updateEvent(
            @RequestHeader("X-User-Role") String role,
            @PathVariable String eventId,
            @Valid @RequestBody UpdateEventRequest request
    ) {
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException(role);
        }
        return eventService.updateEvent(eventId, request);
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(
            @RequestHeader("X-User-Role") String role,
            @PathVariable String eventId
    ) {
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException(role);
        }
        eventService.deleteEvent(eventId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{eventId}/tiers/{tierId}/availability")
    public TierAvailabilityResponse checkAvailability(
            @PathVariable String eventId,
            @PathVariable String tierId,
            @RequestParam int quantity
    ) {
        return eventService.checkAvailability(eventId, tierId, quantity);
    }

    @PostMapping("/{eventId}/tiers/{tierId}/reserve")
    public ResponseEntity<Void> reserveInventory(
            @PathVariable String eventId,
            @PathVariable String tierId,
            @Valid @RequestBody InventoryAdjustmentRequest request
    ) {
        eventService.reserveInventory(eventId, tierId, request.quantity());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{eventId}/tiers/{tierId}/release")
    public ResponseEntity<Void> releaseInventory(
            @PathVariable String eventId,
            @PathVariable String tierId,
            @Valid @RequestBody InventoryAdjustmentRequest request
    ) {
        eventService.releaseInventory(eventId, tierId, request.quantity());
        return ResponseEntity.ok().build();
    }
}
