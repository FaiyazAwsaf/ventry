package com.ventry.booking.projection;

import com.ventry.booking.entity.Booking;
import com.ventry.booking.entity.BookingView;
import com.ventry.booking.repository.BookingViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingViewProjectorTest {

    @Mock
    private BookingViewRepository bookingViewRepository;

    private BookingViewProjector projector;

    @BeforeEach
    void setUp() {
        projector = new BookingViewProjector(bookingViewRepository);
    }

    @Test
    void project_createsNewViewOnFirstEventWithNamesCaptured() {
        Booking booking = new Booking("customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        when(bookingViewRepository.findById(booking.getBookingId())).thenReturn(Optional.empty());

        projector.project(booking, "Concert Night", "Gold");

        ArgumentCaptor<BookingView> captor = ArgumentCaptor.forClass(BookingView.class);
        verify(bookingViewRepository).save(captor.capture());

        BookingView saved = captor.getValue();
        assertThat(saved.getBookingId()).isEqualTo(booking.getBookingId());
        assertThat(saved.getCustomerId()).isEqualTo("customer-1");
        assertThat(saved.getEventId()).isEqualTo("event-1");
        assertThat(saved.getEventName()).isEqualTo("Concert Night");
        assertThat(saved.getTierId()).isEqualTo("tier-1");
        assertThat(saved.getTierName()).isEqualTo("Gold");
        assertThat(saved.getQuantity()).isEqualTo(2);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(saved.getStatus()).isEqualTo(Booking.Status.PENDING);
    }

    @Test
    void project_updatesStatusOnlyWhenViewAlreadyExists_preservingOriginalNames() {
        Booking booking = new Booking("customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        booking.confirm();

        BookingView existingView = new BookingView(
                booking.getBookingId(), "customer-1", "event-1", "Concert Night",
                "tier-1", "Gold", 2, BigDecimal.valueOf(1000), Booking.Status.PENDING
        );
        when(bookingViewRepository.findById(booking.getBookingId())).thenReturn(Optional.of(existingView));

        // Every transition after the first passes no names - only BookingEventStore's
        // initial call (BOOKING_INITIATED) ever has real ones to give.
        projector.project(booking, null, null);

        ArgumentCaptor<BookingView> captor = ArgumentCaptor.forClass(BookingView.class);
        verify(bookingViewRepository).save(captor.capture());

        BookingView saved = captor.getValue();
        assertThat(saved).isSameAs(existingView);
        assertThat(saved.getStatus()).isEqualTo(Booking.Status.CONFIRMED);
        assertThat(saved.getEventName()).isEqualTo("Concert Night");
        assertThat(saved.getTierName()).isEqualTo("Gold");
    }

    @Test
    void project_isSafeToCallRepeatedlyForTheSameState() {
        Booking booking = new Booking("customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        booking.confirm();

        BookingView existingView = new BookingView(
                booking.getBookingId(), "customer-1", "event-1", "Concert Night",
                "tier-1", "Gold", 2, BigDecimal.valueOf(1000), Booking.Status.CONFIRMED
        );
        when(bookingViewRepository.findById(booking.getBookingId())).thenReturn(Optional.of(existingView));

        projector.project(booking, null, null);
        projector.project(booking, null, null);

        verify(bookingViewRepository, times(2)).save(existingView);
        assertThat(existingView.getStatus()).isEqualTo(Booking.Status.CONFIRMED);
    }
}
