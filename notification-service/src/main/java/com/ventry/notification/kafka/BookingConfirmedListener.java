package com.ventry.notification.kafka;

import com.ventry.common.events.BookingConfirmedEvent;
import com.ventry.notification.entity.Notification;
import com.ventry.notification.service.NotificationDispatchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter: all business logic (including the idempotency guard against
 * redelivered events) lives in NotificationDispatchService. groupId comes from
 * spring.kafka.consumer.group-id in application.yml, not repeated here.
 */
@Component
public class BookingConfirmedListener {

    private final NotificationDispatchService notificationDispatchService;

    public BookingConfirmedListener(NotificationDispatchService notificationDispatchService) {
        this.notificationDispatchService = notificationDispatchService;
    }

    @KafkaListener(topics = "booking.confirmed")
    public void handleBookingConfirmed(BookingConfirmedEvent event) {
        notificationDispatchService.dispatch(Notification.Type.BOOKING_CONFIRMED, event.bookingId(), event.customerId(),
                "Your booking " + event.bookingId() + " is confirmed!");
    }
}
