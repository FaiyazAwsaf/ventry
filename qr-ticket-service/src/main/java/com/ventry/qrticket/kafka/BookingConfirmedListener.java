package com.ventry.qrticket.kafka;

import com.ventry.common.events.BookingConfirmedEvent;
import com.ventry.qrticket.service.TicketService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter: all business logic (including the idempotency guard against redelivered
 * events) lives in TicketService. groupId comes from spring.kafka.consumer.group-id in
 * application.yml, not repeated here - one source of truth for the consumer group.
 */
@Component
public class BookingConfirmedListener {

    private final TicketService ticketService;

    public BookingConfirmedListener(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @KafkaListener(topics = "booking.confirmed")
    public void handleBookingConfirmed(BookingConfirmedEvent event) {
        ticketService.generateTicket(event);
    }
}
