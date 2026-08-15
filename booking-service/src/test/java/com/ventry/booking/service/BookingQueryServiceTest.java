package com.ventry.booking.service;

import com.ventry.booking.dto.BookingViewResponse;
import com.ventry.booking.entity.Booking;
import com.ventry.booking.entity.BookingView;
import com.ventry.booking.exception.BookingNotFoundException;
import com.ventry.booking.exception.BookingOwnershipException;
import com.ventry.booking.repository.BookingViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingQueryServiceTest {

    @Mock
    private BookingViewRepository bookingViewRepository;

    private BookingQueryService bookingQueryService;

    @BeforeEach
    void setUp() {
        bookingQueryService = new BookingQueryService(bookingViewRepository);
    }

    private BookingView sampleView(String bookingId, String customerId) {
        return new BookingView(
                bookingId, customerId, "event-1", "Concert Night",
                "tier-1", "Gold", 2, BigDecimal.valueOf(1000), Booking.Status.CONFIRMED
        );
    }

    @Test
    void getBooking_returnsViewForOwner() {
        BookingView view = sampleView("booking-1", "customer-1");
        when(bookingViewRepository.findById("booking-1")).thenReturn(Optional.of(view));

        BookingViewResponse response = bookingQueryService.getBooking("booking-1", "customer-1");

        assertThat(response.bookingId()).isEqualTo("booking-1");
        assertThat(response.customerId()).isEqualTo("customer-1");
        assertThat(response.eventName()).isEqualTo("Concert Night");
        assertThat(response.tierName()).isEqualTo("Gold");
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(response.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void getBooking_throwsNotFoundWhenNoViewExists() {
        when(bookingViewRepository.findById("missing-booking")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingQueryService.getBooking("missing-booking", "customer-1"))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void getBooking_throwsOwnershipExceptionForNonOwner() {
        BookingView view = sampleView("booking-1", "customer-1");
        when(bookingViewRepository.findById("booking-1")).thenReturn(Optional.of(view));

        assertThatThrownBy(() -> bookingQueryService.getBooking("booking-1", "someone-else"))
                .isInstanceOf(BookingOwnershipException.class);
    }

    @Test
    void getHistory_returnsViewsForRequestedCustomerOnly() {
        BookingView view = sampleView("booking-1", "customer-1");
        when(bookingViewRepository.findByCustomerIdOrderByLastUpdatedDesc("customer-1"))
                .thenReturn(List.of(view));

        List<BookingViewResponse> history = bookingQueryService.getHistory("customer-1");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).bookingId()).isEqualTo("booking-1");
        assertThat(history.get(0).customerId()).isEqualTo("customer-1");
    }

    @Test
    void getHistory_returnsEmptyListWhenCustomerHasNoBookings() {
        when(bookingViewRepository.findByCustomerIdOrderByLastUpdatedDesc("customer-1"))
                .thenReturn(List.of());

        List<BookingViewResponse> history = bookingQueryService.getHistory("customer-1");

        assertThat(history).isEmpty();
    }
}
