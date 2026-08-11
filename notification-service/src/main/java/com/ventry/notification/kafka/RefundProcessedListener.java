package com.ventry.notification.kafka;

import com.ventry.common.events.RefundProcessedEvent;
import com.ventry.notification.entity.Notification;
import com.ventry.notification.service.NotificationDispatchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter, same shape as BookingConfirmedListener - all business logic lives in
 * NotificationDispatchService.
 */
@Component
public class RefundProcessedListener {

    private final NotificationDispatchService notificationDispatchService;

    public RefundProcessedListener(NotificationDispatchService notificationDispatchService) {
        this.notificationDispatchService = notificationDispatchService;
    }

    @KafkaListener(topics = "refund.processed")
    public void handleRefundProcessed(RefundProcessedEvent event) {
        notificationDispatchService.dispatch(Notification.Type.REFUND_PROCESSED, event.bookingId(), event.customerId(),
                "Your refund for booking " + event.bookingId() + " has been processed.");
    }
}
