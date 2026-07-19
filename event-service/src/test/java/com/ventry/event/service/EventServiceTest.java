package com.ventry.event.service;

import com.ventry.event.dto.CreateEventRequest;
import com.ventry.event.dto.CreateTierRequest;
import com.ventry.event.dto.EventResponse;
import com.ventry.event.entity.Event;
import com.ventry.event.exception.EventNotFoundException;
import com.ventry.event.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    void createEvent_savesEventWithTiersAndReturnsResponse() {
        CreateEventRequest request = new CreateEventRequest(
                "Coldplay Live",
                "World tour stop",
                LocalDateTime.now().plusDays(30),
                "Army Stadium",
                "https://example.com/banner.png",
                List.of(new CreateTierRequest("Gold", new BigDecimal("5000"), 100))
        );

        EventResponse response = eventService.createEvent(request);

        assertThat(response.name()).isEqualTo("Coldplay Live");
        assertThat(response.tiers()).hasSize(1);
        assertThat(response.tiers().get(0).name()).isEqualTo("Gold");
        assertThat(response.tiers().get(0).available()).isEqualTo(100);

        verify(eventRepository).save(argThat(event ->
                event.getName().equals("Coldplay Live") && event.getTiers().size() == 1
        ));
    }

    @Test
    void browseEvents_mapsAllEventsToResponses() {
        Event event = new Event("Concert", "desc", LocalDateTime.now().plusDays(10), "Venue", null);
        when(eventRepository.findAll()).thenReturn(List.of(event));

        List<EventResponse> responses = eventService.browseEvents();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Concert");
    }

    @Test
    void getEvent_returnsResponseWhenFound() {
        Event event = new Event("Concert", "desc", LocalDateTime.now().plusDays(10), "Venue", null);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        EventResponse response = eventService.getEvent(event.getId());

        assertThat(response.id()).isEqualTo(event.getId());
    }

    @Test
    void getEvent_throwsWhenNotFound() {
        when(eventRepository.findById("missing-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEvent("missing-id"))
                .isInstanceOf(EventNotFoundException.class);
    }
}
