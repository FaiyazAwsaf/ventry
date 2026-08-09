package com.ventry.qrticket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ventry.common.events.BookingConfirmedEvent;
import com.ventry.common.events.TicketGeneratedEvent;
import com.ventry.qrticket.dto.QrPayload;
import com.ventry.qrticket.entity.Ticket;
import com.ventry.qrticket.kafka.TicketEventProducer;
import com.ventry.qrticket.repository.TicketRepository;
import org.springframework.stereotype.Service;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketEventProducer ticketEventProducer;
    private final ObjectMapper objectMapper;

    public TicketService(TicketRepository ticketRepository, TicketEventProducer ticketEventProducer,
                          ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.ticketEventProducer = ticketEventProducer;
        this.objectMapper = objectMapper;
    }

    /**
     * Idempotent on bookingId - booking.confirmed can be redelivered (Kafka at-least-once), and
     * a ticket must only ever be generated once per booking. Silent no-op on redelivery, same
     * reasoning as Booking.confirm()/markFailed(): this guards against the same internal event
     * arriving twice, unlike Ticket.validate()'s guard against a genuinely repeat physical scan.
     */
    public void generateTicket(BookingConfirmedEvent event) {
        if (ticketRepository.existsByBookingId(event.bookingId())) {
            return;
        }

        String qrContent = buildQrContent(event);
        Ticket ticket = new Ticket(
                event.bookingId(), event.eventId(), event.tierId(), event.customerId(), event.quantity(), qrContent);
        ticketRepository.save(ticket);

        ticketEventProducer.publishTicketGenerated(new TicketGeneratedEvent(event.bookingId()));
    }

    private String buildQrContent(BookingConfirmedEvent event) {
        try {
            return objectMapper.writeValueAsString(
                    new QrPayload(event.bookingId(), event.eventId(), event.tierId(), event.customerId()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build QR content for booking " + event.bookingId(), e);
        }
    }
}
