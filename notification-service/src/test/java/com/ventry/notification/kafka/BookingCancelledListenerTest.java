package com.ventry.notification.kafka;

import com.ventry.common.events.BookingCancelledEvent;
import com.ventry.notification.entity.Notification;
import com.ventry.notification.service.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingCancelledListenerTest {

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @InjectMocks
    private BookingCancelledListener listener;

    @Test
    void handleBookingCancelled_dispatchesBookingCancelledNotification() {
        BookingCancelledEvent event = new BookingCancelledEvent("booking-1", "customer-1", BigDecimal.valueOf(1000));

        listener.handleBookingCancelled(event);

        verify(notificationDispatchService).dispatch(Notification.Type.BOOKING_CANCELLED, "booking-1", "customer-1",
                "Your booking booking-1 has been cancelled.");
    }
}
