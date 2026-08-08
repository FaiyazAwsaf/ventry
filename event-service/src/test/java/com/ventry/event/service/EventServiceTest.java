package com.ventry.event.service;

import com.ventry.event.dto.CreateEventRequest;
import com.ventry.event.dto.CreateTierRequest;
import com.ventry.event.dto.EventAnalyticsResponse;
import com.ventry.event.dto.EventResponse;
import com.ventry.event.dto.TierAvailabilityResponse;
import com.ventry.event.entity.Event;
import com.ventry.event.entity.TicketTier;
import com.ventry.event.exception.EventNotFoundException;
import com.ventry.event.exception.InsufficientInventoryException;
import com.ventry.event.exception.TierNotFoundException;
import com.ventry.event.repository.EventRepository;
import com.ventry.event.repository.TicketTierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Mock
    private TicketTierRepository ticketTierRepository;

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

    @Test
    void checkAvailability_returnsTrueWhenEnoughStock() {
        TicketTier tier = new TicketTier("Gold", new BigDecimal("100"), 10);
        when(ticketTierRepository.findByIdAndEventId(tier.getId(), "event-1")).thenReturn(Optional.of(tier));

        TierAvailabilityResponse response = eventService.checkAvailability("event-1", tier.getId(), 5);

        assertThat(response.available()).isTrue();
        assertThat(response.remaining()).isEqualTo(10);
    }

    @Test
    void checkAvailability_throwsWhenTierNotFound() {
        when(ticketTierRepository.findByIdAndEventId("missing-tier", "event-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.checkAvailability("event-1", "missing-tier", 1))
                .isInstanceOf(TierNotFoundException.class);
    }

    @Test
    void reserveInventory_succeedsWhenRowsUpdated() {
        when(ticketTierRepository.reserve("tier-1", "event-1", 2)).thenReturn(1);

        eventService.reserveInventory("event-1", "tier-1", 2);

        verify(ticketTierRepository).reserve("tier-1", "event-1", 2);
    }

    @Test
    void reserveInventory_throwsInsufficientInventoryWhenTierExistsButUpdateAffectsNoRows() {
        when(ticketTierRepository.reserve("tier-1", "event-1", 2)).thenReturn(0);
        when(ticketTierRepository.existsByIdAndEventId("tier-1", "event-1")).thenReturn(true);

        assertThatThrownBy(() -> eventService.reserveInventory("event-1", "tier-1", 2))
                .isInstanceOf(InsufficientInventoryException.class);
    }

    @Test
    void reserveInventory_throwsTierNotFoundWhenTierDoesNotExist() {
        when(ticketTierRepository.reserve("tier-1", "event-1", 2)).thenReturn(0);
        when(ticketTierRepository.existsByIdAndEventId("tier-1", "event-1")).thenReturn(false);

        assertThatThrownBy(() -> eventService.reserveInventory("event-1", "tier-1", 2))
                .isInstanceOf(TierNotFoundException.class);
    }

    @Test
    void releaseInventory_isSilentNoOpWhenGuardTripsButTierExists() {
        when(ticketTierRepository.release("tier-1", "event-1", 2)).thenReturn(0);
        when(ticketTierRepository.existsByIdAndEventId("tier-1", "event-1")).thenReturn(true);

        eventService.releaseInventory("event-1", "tier-1", 2);

        verify(ticketTierRepository).release("tier-1", "event-1", 2);
    }

    @Test
    void releaseInventory_throwsTierNotFoundWhenTierDoesNotExist() {
        when(ticketTierRepository.release("tier-1", "event-1", 2)).thenReturn(0);
        when(ticketTierRepository.existsByIdAndEventId("tier-1", "event-1")).thenReturn(false);

        assertThatThrownBy(() -> eventService.releaseInventory("event-1", "tier-1", 2))
                .isInstanceOf(TierNotFoundException.class);
    }

    @Test
    void getEventAnalytics_reportsZeroReservedAndRevenueForFreshEvent() {
        Event event = new Event("Concert", "desc", LocalDateTime.now().plusDays(10), "Venue", null);
        event.addTier(new TicketTier("Gold", new BigDecimal("100"), 10));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        EventAnalyticsResponse response = eventService.getEventAnalytics(event.getId());

        assertThat(response.totalCapacity()).isEqualTo(10);
        assertThat(response.totalReserved()).isEqualTo(0);
        assertThat(response.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.tiers().get(0).reserved()).isEqualTo(0);
    }

    @Test
    void getEventAnalytics_computesReservedAndRevenueFromAvailableGap() {
        Event event = new Event("Concert", "desc", LocalDateTime.now().plusDays(10), "Venue", null);
        TicketTier tier = new TicketTier("Gold", new BigDecimal("100"), 10);
        event.addTier(tier);
        // TicketTier deliberately has no public setter for `available` - inventory only
        // changes via the repository's atomic @Modifying reserve/release queries, never
        // through the entity. Forcing it here is the only way to simulate "some reserved"
        // state without going through a real DB.
        ReflectionTestUtils.setField(tier, "available", 4);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        EventAnalyticsResponse response = eventService.getEventAnalytics(event.getId());

        assertThat(response.totalReserved()).isEqualTo(6);
        assertThat(response.totalRevenue()).isEqualByComparingTo(new BigDecimal("600"));
        assertThat(response.tiers().get(0).available()).isEqualTo(4);
    }

    @Test
    void getEventAnalytics_throwsWhenEventNotFound() {
        when(eventRepository.findById("missing-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEventAnalytics("missing-id"))
                .isInstanceOf(EventNotFoundException.class);
    }
}
