package com.ventry.event.controller;

import com.ventry.event.dto.CreateEventRequest;
import com.ventry.event.dto.EventResponse;
import com.ventry.event.dto.UpdateEventRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(request));
    }

    @PutMapping("/{eventId}")
    public EventResponse updateEvent(@PathVariable String eventId, @Valid @RequestBody UpdateEventRequest request) {
        return eventService.updateEvent(eventId, request);
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(@PathVariable String eventId) {
        eventService.deleteEvent(eventId);
        return ResponseEntity.noContent().build();
    }
}
