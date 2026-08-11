package com.ventry.payment.kafka;

import com.ventry.common.events.BookingCancelledEvent;
import com.ventry.common.events.RefundProcessedEvent;
import com.ventry.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter: all business logic (including the idempotency guard against
 * redelivered events) lives in PaymentService. Same shape as BookingInitiatedListener - the
 * service does the work and reports the outcome, this listener branches and publishes.
 */
@Component
public class BookingCancelledListener {

    private static final String REFUND_PROCESSED_TOPIC = "refund.processed";

    private final PaymentService paymentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BookingCancelledListener(PaymentService paymentService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentService = paymentService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "booking.cancelled")
    public void handleBookingCancelled(BookingCancelledEvent event) {
        if (paymentService.processRefund(event)) {
            kafkaTemplate.send(REFUND_PROCESSED_TOPIC, event.bookingId(),
                    new RefundProcessedEvent(event.bookingId(), event.customerId()));
        }
    }
}
