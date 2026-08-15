package com.ventry.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ventry.booking.dto.BookingReplayResponse;
import com.ventry.booking.entity.Booking;
import com.ventry.booking.entity.BookingEvent;
import com.ventry.booking.exception.BookingNotFoundException;
import com.ventry.booking.exception.BookingOwnershipException;
import com.ventry.booking.repository.BookingEventRepository;
import com.ventry.common.events.BookingInitiatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Uses a real ObjectMapper, not a mock - the JSON round trip (serialize an event the same way
 * BookingEventStore does, then have the service deserialize it back) is exactly the behavior
 * worth verifying for real here, not something to fake away.
 */
@ExtendWith(MockitoExtension.class)
class BookingReplayServiceTest {

    @Mock
    private BookingEventRepository bookingEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BookingReplayService bookingReplayService;

    @BeforeEach
    void setUp() {
        bookingReplayService = new BookingReplayService(bookingEventRepository, objectMapper);
    }

    private BookingEvent initiatedEvent(String bookingId) {
        try {
            String payload = objectMapper.writeValueAsString(
                    new BookingInitiatedEvent(bookingId, "customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000)));
            return new BookingEvent(bookingId, "BOOKING_INITIATED", payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void replay_throwsNotFoundWhenNoEventsExist() {
        when(bookingEventRepository.findByBookingIdOrderByTimestampAsc("missing-booking"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> bookingReplayService.replay("missing-booking", "customer-1"))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void replay_throwsOwnershipExceptionForNonOwner() {
        when(bookingEventRepository.findByBookingIdOrderByTimestampAsc("booking-1"))
                .thenReturn(List.of(initiatedEvent("booking-1")));

        assertThatThrownBy(() -> bookingReplayService.replay("booking-1", "someone-else"))
                .isInstanceOf(BookingOwnershipException.class);
    }

    @Test
    void replay_reconstructsPendingStateFromInitiatedEventAlone() {
        when(bookingEventRepository.findByBookingIdOrderByTimestampAsc("booking-1"))
                .thenReturn(List.of(initiatedEvent("booking-1")));

        BookingReplayResponse response = bookingReplayService.replay("booking-1", "customer-1");

        assertThat(response.bookingId()).isEqualTo("booking-1");
        assertThat(response.customerId()).isEqualTo("customer-1");
        assertThat(response.eventId()).isEqualTo("event-1");
        assertThat(response.tierId()).isEqualTo("tier-1");
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(response.status()).isEqualTo(Booking.Status.PENDING.name());
        assertThat(response.eventsReplayed()).isEqualTo(1);
    }

    @Test
    void replay_reconstructsConfirmedStateAfterConfirmedEvent() {
        when(bookingEventRepository.findByBookingIdOrderByTimestampAsc("booking-1"))
                .thenReturn(List.of(
                        initiatedEvent("booking-1"),
                        new BookingEvent("booking-1", "BOOKING_CONFIRMED", "{}")
                ));

        BookingReplayResponse response = bookingReplayService.replay("booking-1", "customer-1");

        assertThat(response.status()).isEqualTo(Booking.Status.CONFIRMED.name());
        assertThat(response.eventsReplayed()).isEqualTo(2);
    }

    @Test
    void replay_reconstructsFailedStateAfterFailedEvent() {
        when(bookingEventRepository.findByBookingIdOrderByTimestampAsc("booking-1"))
                .thenReturn(List.of(
                        initiatedEvent("booking-1"),
                        new BookingEvent("booking-1", "BOOKING_FAILED", "{}")
                ));

        BookingReplayResponse response = bookingReplayService.replay("booking-1", "customer-1");

        assertThat(response.status()).isEqualTo(Booking.Status.FAILED.name());
    }

    @Test
    void replay_reconstructsCancelledStateAfterFullCancellationLifecycle() {
        when(bookingEventRepository.findByBookingIdOrderByTimestampAsc("booking-1"))
                .thenReturn(List.of(
                        initiatedEvent("booking-1"),
                        new BookingEvent("booking-1", "BOOKING_CONFIRMED", "{}"),
                        new BookingEvent("booking-1", "BOOKING_CANCELLATION_REQUESTED", "{}"),
                        new BookingEvent("booking-1", "BOOKING_CANCELLED", "{}")
                ));

        BookingReplayResponse response = bookingReplayService.replay("booking-1", "customer-1");

        assertThat(response.status()).isEqualTo(Booking.Status.CANCELLED.name());
        assertThat(response.eventsReplayed()).isEqualTo(4);
    }

    @Test
    void replay_ignoresUnknownEventTypeAndKeepsCurrentStatus() {
        when(bookingEventRepository.findByBookingIdOrderByTimestampAsc("booking-1"))
                .thenReturn(List.of(
                        initiatedEvent("booking-1"),
                        new BookingEvent("booking-1", "BOOKING_CONFIRMED", "{}"),
                        new BookingEvent("booking-1", "SOME_FUTURE_EVENT_TYPE", "{}")
                ));

        BookingReplayResponse response = bookingReplayService.replay("booking-1", "customer-1");

        assertThat(response.status()).isEqualTo(Booking.Status.CONFIRMED.name());
        assertThat(response.eventsReplayed()).isEqualTo(3);
    }
}
