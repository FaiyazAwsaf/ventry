package com.ventry.booking.service;

import com.ventry.booking.client.EventServiceClient;
import com.ventry.booking.dto.BookingResponse;
import com.ventry.booking.dto.CreateBookingRequest;
import com.ventry.booking.entity.Booking;
import com.ventry.booking.exception.EventOrTierNotFoundException;
import com.ventry.booking.exception.TierUnavailableException;
import com.ventry.booking.kafka.BookingEventProducer;
import com.ventry.common.events.BookingInitiatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private EventServiceClient eventServiceClient;

    @Mock
    private BookingEventStore bookingEventStore;

    @Mock
    private BookingEventProducer bookingEventProducer;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void createBooking_reservesInventoryPersistsAndPublishesOnSuccess() {
        CreateBookingRequest request = new CreateBookingRequest("event-1", "tier-1", 2);
        when(eventServiceClient.getTierPrice("event-1", "tier-1")).thenReturn(BigDecimal.valueOf(500));

        BookingResponse response = bookingService.createBooking("customer-1", request);

        assertThat(response.customerId()).isEqualTo("customer-1");
        assertThat(response.eventId()).isEqualTo("event-1");
        assertThat(response.tierId()).isEqualTo("tier-1");
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(response.status()).isEqualTo("PENDING");

        verify(eventServiceClient).reserveInventory("event-1", "tier-1", 2);

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        ArgumentCaptor<BookingInitiatedEvent> eventCaptor = ArgumentCaptor.forClass(BookingInitiatedEvent.class);
        verify(bookingEventStore).append(bookingCaptor.capture(), eq("BOOKING_INITIATED"), eventCaptor.capture());

        assertThat(bookingCaptor.getValue().getCustomerId()).isEqualTo("customer-1");
        assertThat(eventCaptor.getValue().bookingId()).isEqualTo(bookingCaptor.getValue().getBookingId());
        assertThat(eventCaptor.getValue().totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        verify(bookingEventProducer).publishBookingInitiated(eventCaptor.getValue());
    }

    @Test
    void createBooking_propagatesNotFoundWithoutReservingOrPersisting() {
        CreateBookingRequest request = new CreateBookingRequest("event-1", "missing-tier", 1);
        when(eventServiceClient.getTierPrice("event-1", "missing-tier"))
                .thenThrow(new EventOrTierNotFoundException("event-1", "missing-tier"));

        assertThatThrownBy(() -> bookingService.createBooking("customer-1", request))
                .isInstanceOf(EventOrTierNotFoundException.class);

        verify(eventServiceClient, never()).reserveInventory(any(), any(), anyInt());
        verify(bookingEventStore, never()).append(any(), any(), any());
        verify(bookingEventProducer, never()).publishBookingInitiated(any());
    }

    @Test
    void createBooking_propagatesConflictWithoutPersistingWhenReservationFails() {
        CreateBookingRequest request = new CreateBookingRequest("event-1", "tier-1", 5);
        when(eventServiceClient.getTierPrice("event-1", "tier-1")).thenReturn(BigDecimal.valueOf(200));
        doThrow(new TierUnavailableException("event-1", "tier-1", 5))
                .when(eventServiceClient).reserveInventory("event-1", "tier-1", 5);

        assertThatThrownBy(() -> bookingService.createBooking("customer-1", request))
                .isInstanceOf(TierUnavailableException.class);

        verify(bookingEventStore, never()).append(any(), any(), any());
        verify(bookingEventProducer, never()).publishBookingInitiated(any());
    }
}
