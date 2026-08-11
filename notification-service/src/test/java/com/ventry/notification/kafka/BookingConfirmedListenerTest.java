package com.ventry.notification.kafka;

import com.ventry.common.events.BookingConfirmedEvent;
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
class BookingConfirmedListenerTest {

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @InjectMocks
    private BookingConfirmedListener listener;

    @Test
    void handleBookingConfirmed_dispatchesBookingConfirmedNotification() {
        BookingConfirmedEvent event = new BookingConfirmedEvent(
                "booking-1", "customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));

        listener.handleBookingConfirmed(event);

        verify(notificationDispatchService).dispatch(Notification.Type.BOOKING_CONFIRMED, "booking-1", "customer-1",
                "Your booking booking-1 is confirmed!");
    }
}
