package com.ventry.booking.kafka;

import com.ventry.booking.service.BookingService;
import com.ventry.common.events.RefundProcessedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter: all business logic (including the idempotency guard against
 * redelivered events) lives in BookingService. Same shape as PaymentResultListener.
 */
@Component
public class RefundProcessedListener {

    private final BookingService bookingService;

    public RefundProcessedListener(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @KafkaListener(topics = "refund.processed")
    public void handleRefundProcessed(RefundProcessedEvent event) {
        bookingService.completeCancellation(event);
    }
}
