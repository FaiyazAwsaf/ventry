package com.ventry.booking.service;

import com.ventry.booking.client.EventServiceClient;
import com.ventry.booking.dto.BookingResponse;
import com.ventry.booking.dto.CreateBookingRequest;
import com.ventry.booking.entity.Booking;
import com.ventry.booking.exception.BookingNotCancellableException;
import com.ventry.booking.exception.BookingNotFoundException;
import com.ventry.booking.exception.BookingOwnershipException;
import com.ventry.booking.exception.EventOrTierNotFoundException;
import com.ventry.booking.exception.TierUnavailableException;
import com.ventry.booking.kafka.BookingEventProducer;
import com.ventry.booking.repository.BookingRepository;
import com.ventry.common.events.BookingCancelledEvent;
import com.ventry.common.events.BookingConfirmedEvent;
import com.ventry.common.events.BookingInitiatedEvent;
import com.ventry.common.events.PaymentFailedEvent;
import com.ventry.common.events.PaymentSuccessEvent;
import com.ventry.common.events.RefundProcessedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

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
    private BookingRepository bookingRepository;

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

    @Test
    void confirmBooking_transitionsPendingBookingAndPublishesBookingConfirmed() {
        Booking booking = new Booking("customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        String bookingId = booking.getBookingId();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        bookingService.confirmBooking(new PaymentSuccessEvent(bookingId, BigDecimal.valueOf(1000)));

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.CONFIRMED);

        ArgumentCaptor<BookingConfirmedEvent> captor = ArgumentCaptor.forClass(BookingConfirmedEvent.class);
        verify(bookingEventStore).append(eq(booking), eq("BOOKING_CONFIRMED"), captor.capture());
        assertThat(captor.getValue().bookingId()).isEqualTo(bookingId);
        assertThat(captor.getValue().totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        verify(bookingEventProducer).publishBookingConfirmed(captor.getValue());
    }

    @Test
    void confirmBooking_redeliveredEventOnAlreadyConfirmedBookingIsNoOp() {
        Booking booking = new Booking("customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        booking.confirm();
        String bookingId = booking.getBookingId();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        bookingService.confirmBooking(new PaymentSuccessEvent(bookingId, BigDecimal.valueOf(1000)));

        verify(bookingEventStore, never()).append(any(), any(), any());
        verify(bookingEventProducer, never()).publishBookingConfirmed(any());
    }

    @Test
    void confirmBooking_throwsWhenBookingUnknown() {
        when(bookingRepository.findById("missing-booking")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                bookingService.confirmBooking(new PaymentSuccessEvent("missing-booking", BigDecimal.TEN)))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void failBooking_transitionsPendingBookingAndReleasesInventory() {
        Booking booking = new Booking("customer-1", "event-1", "tier-1", 3, BigDecimal.valueOf(1500));
        String bookingId = booking.getBookingId();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        PaymentFailedEvent event = new PaymentFailedEvent(bookingId, "Payment declined by BKASH");
        bookingService.failBooking(event);

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.FAILED);
        verify(bookingEventStore).append(booking, "BOOKING_FAILED", event);
        verify(eventServiceClient).releaseInventory("event-1", "tier-1", 3);
    }

    @Test
    void failBooking_redeliveredEventOnAlreadyFailedBookingIsNoOp() {
        Booking booking = new Booking("customer-1", "event-1", "tier-1", 3, BigDecimal.valueOf(1500));
        booking.markFailed();
        String bookingId = booking.getBookingId();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        bookingService.failBooking(new PaymentFailedEvent(bookingId, "Payment declined by BKASH"));

        verify(bookingEventStore, never()).append(any(), any(), any());
        verify(eventServiceClient, never()).releaseInventory(any(), any(), anyInt());
    }

    @Test
    void cancelBooking_transitionsConfirmedBookingAndPublishesBookingCancelled() {
        Booking booking = new Booking("customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        booking.confirm();
        String bookingId = booking.getBookingId();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.cancelBooking(bookingId, "customer-1");

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.CANCELLATION_PENDING);
        assertThat(response.status()).isEqualTo("CANCELLATION_PENDING");

        ArgumentCaptor<BookingCancelledEvent> captor = ArgumentCaptor.forClass(BookingCancelledEvent.class);
        verify(bookingEventStore).append(eq(booking), eq("BOOKING_CANCELLATION_REQUESTED"), captor.capture());
        assertThat(captor.getValue().bookingId()).isEqualTo(bookingId);
        assertThat(captor.getValue().totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        verify(bookingEventProducer).publishBookingCancelled(captor.getValue());
    }

    @Test
    void cancelBooking_throwsOwnershipExceptionForNonOwner() {
        Booking booking = new Booking("customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        booking.confirm();
        String bookingId = booking.getBookingId();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId, "someone-else"))
                .isInstanceOf(BookingOwnershipException.class);

        verify(bookingEventStore, never()).append(any(), any(), any());
        verify(bookingEventProducer, never()).publishBookingCancelled(any());
    }

    @Test
    void cancelBooking_throwsNotCancellableWhenStillPending() {
        Booking booking = new Booking("customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        String bookingId = booking.getBookingId();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId, "customer-1"))
                .isInstanceOf(BookingNotCancellableException.class);

        verify(bookingEventStore, never()).append(any(), any(), any());
        verify(bookingEventProducer, never()).publishBookingCancelled(any());
    }

    @Test
    void cancelBooking_throwsWhenBookingUnknown() {
        when(bookingRepository.findById("missing-booking")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking("missing-booking", "customer-1"))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void completeCancellation_transitionsCancellationPendingBookingAndReleasesInventory() {
        Booking booking = new Booking("customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        booking.confirm();
        booking.cancel();
        String bookingId = booking.getBookingId();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        RefundProcessedEvent event = new RefundProcessedEvent(bookingId);
        bookingService.completeCancellation(event);

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.CANCELLED);
        verify(bookingEventStore).append(booking, "BOOKING_CANCELLED", event);
        verify(eventServiceClient).releaseInventory("event-1", "tier-1", 2);
    }

    @Test
    void completeCancellation_redeliveredEventOnAlreadyCancelledBookingIsNoOp() {
        Booking booking = new Booking("customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        booking.confirm();
        booking.cancel();
        booking.completeCancellation();
        String bookingId = booking.getBookingId();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        bookingService.completeCancellation(new RefundProcessedEvent(bookingId));

        verify(bookingEventStore, never()).append(any(), any(), any());
        verify(eventServiceClient, never()).releaseInventory(any(), any(), anyInt());
    }

    @Test
    void completeCancellation_throwsWhenBookingUnknown() {
        when(bookingRepository.findById("missing-booking")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.completeCancellation(new RefundProcessedEvent("missing-booking")))
                .isInstanceOf(BookingNotFoundException.class);
    }
}
