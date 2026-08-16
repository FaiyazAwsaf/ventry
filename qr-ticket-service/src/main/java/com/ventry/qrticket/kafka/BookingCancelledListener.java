package com.ventry.qrticket.kafka;

import com.ventry.common.events.BookingCancelledEvent;
import com.ventry.qrticket.service.TicketService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka adapter: all business logic (including the idempotency guard against redelivered
 * events) lives in TicketService. Same shape as BookingConfirmedListener.
 */
@Component
public class BookingCancelledListener {

    private final TicketService ticketService;

    public BookingCancelledListener(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @KafkaListener(topics = "booking.cancelled")
    public void handleBookingCancelled(BookingCancelledEvent event) {
        ticketService.revokeTicket(event);
    }
}
