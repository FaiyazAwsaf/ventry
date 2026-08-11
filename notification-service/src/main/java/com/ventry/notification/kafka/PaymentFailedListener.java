package com.ventry.notification.kafka;

import com.ventry.common.events.PaymentFailedEvent;
import com.ventry.notification.entity.Notification;
import com.ventry.notification.service.NotificationDispatchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter, same shape as BookingConfirmedListener - all business logic lives in
 * NotificationDispatchService.
 */
@Component
public class PaymentFailedListener {

    private final NotificationDispatchService notificationDispatchService;

    public PaymentFailedListener(NotificationDispatchService notificationDispatchService) {
        this.notificationDispatchService = notificationDispatchService;
    }

    @KafkaListener(topics = "payment.failed")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        notificationDispatchService.dispatch(Notification.Type.PAYMENT_FAILED, event.bookingId(), event.customerId(),
                "Your payment for booking " + event.bookingId() + " failed: " + event.reason());
    }
}
