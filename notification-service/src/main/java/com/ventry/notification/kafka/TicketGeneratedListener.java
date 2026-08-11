package com.ventry.notification.kafka;

import com.ventry.common.events.TicketGeneratedEvent;
import com.ventry.notification.entity.Notification;
import com.ventry.notification.service.NotificationDispatchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter, same shape as BookingConfirmedListener - all business logic lives in
 * NotificationDispatchService.
 */
@Component
public class TicketGeneratedListener {

    private final NotificationDispatchService notificationDispatchService;

    public TicketGeneratedListener(NotificationDispatchService notificationDispatchService) {
        this.notificationDispatchService = notificationDispatchService;
    }

    @KafkaListener(topics = "ticket.generated")
    public void handleTicketGenerated(TicketGeneratedEvent event) {
        notificationDispatchService.dispatch(Notification.Type.TICKET_GENERATED, event.bookingId(), event.customerId(),
                "Your ticket for booking " + event.bookingId() + " is ready.");
    }
}
