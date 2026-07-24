package com.ventry.booking.kafka;

import com.ventry.common.events.BookingInitiatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class BookingEventProducer {

    private static final String BOOKING_INITIATED_TOPIC = "booking.initiated";

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
}
