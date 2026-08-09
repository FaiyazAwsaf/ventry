package com.ventry.qrticket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ventry.common.events.BookingConfirmedEvent;
import com.ventry.common.events.TicketGeneratedEvent;
import com.ventry.qrticket.dto.QrPayload;
import com.ventry.qrticket.entity.Ticket;
import com.ventry.qrticket.exception.TicketAlreadyValidatedException;
import com.ventry.qrticket.exception.TicketContentMismatchException;
import com.ventry.qrticket.exception.TicketNotFoundException;
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

    /**
     * bookingId is taken from the parsed scanned content, not a path/request parameter - a real
     * scan device only has whatever's encoded in the QR itself. The stored qrContent is then
     * compared byte-for-byte against what was scanned, which is what actually defends against a
     * forged payload that merely guesses a valid bookingId.
     */
    public Ticket validateTicket(String scannedQrContent) {
        QrPayload payload = parseQrContent(scannedQrContent);
        Ticket ticket = ticketRepository.findByBookingId(payload.bookingId())
                .orElseThrow(() -> new TicketNotFoundException(payload.bookingId()));

        if (!ticket.getQrContent().equals(scannedQrContent)) {
            throw new TicketContentMismatchException();
        }
        if (!ticket.validate()) {
            throw new TicketAlreadyValidatedException(payload.bookingId());
        }
        return ticketRepository.save(ticket);
    }

    private QrPayload parseQrContent(String qrContent) {
        try {
            return objectMapper.readValue(qrContent, QrPayload.class);
        } catch (JsonProcessingException e) {
            throw new TicketContentMismatchException();
        }
    }
}
