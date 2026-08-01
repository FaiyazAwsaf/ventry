package com.ventry.payment.kafka;

import com.ventry.common.events.BookingInitiatedEvent;
import com.ventry.common.events.PaymentFailedEvent;
import com.ventry.common.events.PaymentSuccessEvent;
import com.ventry.payment.entity.Payment;
import com.ventry.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter: all business logic lives in PaymentService. This listener just
 * translates the outcome into the next event in the Saga.
 */
@Component
public class BookingInitiatedListener {

    private static final String PAYMENT_SUCCESS_TOPIC = "payment.success";
    private static final String PAYMENT_FAILED_TOPIC = "payment.failed";

    private final PaymentService paymentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BookingInitiatedListener(PaymentService paymentService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentService = paymentService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * groupId comes from spring.kafka.consumer.group-id in application.yml, not repeated
     * here as an annotation attribute - one source of truth for the consumer group.
     */
    @KafkaListener(topics = "booking.initiated")
    public void handleBookingInitiated(BookingInitiatedEvent event) {
        Payment payment = paymentService.processPayment(event);

        if (payment.getStatus() == Payment.Status.SUCCESS) {
            kafkaTemplate.send(PAYMENT_SUCCESS_TOPIC, event.bookingId(),
                    new PaymentSuccessEvent(event.bookingId(), payment.getAmount()));
        } else {
            kafkaTemplate.send(PAYMENT_FAILED_TOPIC, event.bookingId(),
                    new PaymentFailedEvent(event.bookingId(), "Payment declined by " + payment.getGatewayUsed()));
        }
    }
}
