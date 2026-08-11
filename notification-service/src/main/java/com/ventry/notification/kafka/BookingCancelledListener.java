package com.ventry.notification.kafka;

import com.ventry.common.events.BookingCancelledEvent;
import com.ventry.notification.entity.Notification;
import com.ventry.notification.service.NotificationDispatchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter, same shape as BookingConfirmedListener - all business logic lives in
 * NotificationDispatchService.
 */
@Component
public class BookingCancelledListener {

    private final NotificationDispatchService notificationDispatchService;

    public BookingCancelledListener(NotificationDispatchService notificationDispatchService) {
        this.notificationDispatchService = notificationDispatchService;
    }

    @KafkaListener(topics = "booking.cancelled")
    public void handleBookingCancelled(BookingCancelledEvent event) {
        notificationDispatchService.dispatch(Notification.Type.BOOKING_CANCELLED, event.bookingId(), event.customerId(),
                "Your booking " + event.bookingId() + " has been cancelled.");
    }
}
