package com.ventry.booking.kafka;

import com.ventry.common.events.BookingCancelledEvent;
import com.ventry.common.events.BookingConfirmedEvent;
import com.ventry.common.events.BookingInitiatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class BookingEventProducer {

    private static final String BOOKING_INITIATED_TOPIC = "booking.initiated";
    private static final String BOOKING_CONFIRMED_TOPIC = "booking.confirmed";
    private static final String BOOKING_CANCELLED_TOPIC = "booking.cancelled";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BookingEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Keyed by bookingId so Kafka's own partitioning keeps every event for a given
     * booking in order - important once later sections publish more events per booking.
     */
    public void publishBookingInitiated(BookingInitiatedEvent event) {
        kafkaTemplate.send(BOOKING_INITIATED_TOPIC, event.bookingId(), event);
    }

    /**
     * Consumed next by QR/Ticket Service and Notification Service (Day 9-10) - keyed by
     * bookingId for the same ordering reason as publishBookingInitiated.
     */
    public void publishBookingConfirmed(BookingConfirmedEvent event) {
        kafkaTemplate.send(BOOKING_CONFIRMED_TOPIC, event.bookingId(), event);
    }

    /**
     * Consumed next by Payment Service (initiates the refund) - keyed by bookingId for the
     * same ordering reason as the other publish methods.
     */
    public void publishBookingCancelled(BookingCancelledEvent event) {
        kafkaTemplate.send(BOOKING_CANCELLED_TOPIC, event.bookingId(), event);
    }
}
