package com.ventry.booking.kafka;

import com.ventry.booking.service.BookingService;
import com.ventry.common.events.PaymentFailedEvent;
import com.ventry.common.events.PaymentSuccessEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter: all business logic (including the idempotency guard against
 * redelivered events) lives in BookingService.
 */
@Component
public class PaymentResultListener {

    private final BookingService bookingService;

    public PaymentResultListener(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * groupId comes from spring.kafka.consumer.group-id in application.yml, not repeated
     * here as an annotation attribute - one source of truth for the consumer group.
     */
    @KafkaListener(topics = "payment.success")
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        bookingService.confirmBooking(event);
    }

    @KafkaListener(topics = "payment.failed")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        bookingService.failBooking(event);
    }
}
