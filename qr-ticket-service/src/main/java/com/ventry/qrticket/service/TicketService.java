package com.ventry.qrticket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ventry.common.events.BookingCancelledEvent;
import com.ventry.common.events.BookingConfirmedEvent;
import com.ventry.common.events.TicketGeneratedEvent;
import com.ventry.qrticket.dto.QrPayload;
import com.ventry.qrticket.entity.Ticket;
import com.ventry.qrticket.exception.ForbiddenException;
import com.ventry.qrticket.exception.TicketAlreadyValidatedException;
import com.ventry.qrticket.exception.TicketContentMismatchException;
import com.ventry.qrticket.exception.TicketNotFoundException;
import com.ventry.qrticket.exception.TicketRevokedException;
import com.ventry.qrticket.kafka.TicketEventProducer;
import com.ventry.qrticket.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class TicketService {

    private static final int QR_IMAGE_SIZE = 300;

    private final TicketRepository ticketRepository;
    private final TicketEventProducer ticketEventProducer;
    private final QrCodeGenerator qrCodeGenerator;
    private final ObjectMapper objectMapper;

    public TicketService(TicketRepository ticketRepository, TicketEventProducer ticketEventProducer,
                          QrCodeGenerator qrCodeGenerator, ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.ticketEventProducer = ticketEventProducer;
        this.qrCodeGenerator = qrCodeGenerator;
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

        ticketEventProducer.publishTicketGenerated(new TicketGeneratedEvent(event.bookingId(), event.customerId()));
    }

    /**
     * Consumes booking.cancelled - without this, a cancelled/refunded booking's QR stayed
     * fully scannable forever, since validateTicket()/getQrImage() only ever checked the
     * ticket's own GENERATED/VALIDATED state, never the booking's current status. Silent
     * no-op if no ticket exists yet: booking.cancelled and booking.confirmed are on separate
     * topics with no cross-topic ordering guarantee, so a cancellation could in principle be
     * consumed before the confirmation that generates the ticket.
     */
    public void revokeTicket(BookingCancelledEvent event) {
        ticketRepository.findByBookingId(event.bookingId())
                .filter(Ticket::revoke)
                .ifPresent(ticketRepository::save);
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
        if (ticket.getStatus() == Ticket.Status.REVOKED) {
            throw new TicketRevokedException(payload.bookingId());
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

    /**
     * Regenerates the PNG on every call rather than reading stored bytes - qrContent fully
     * determines the image, and generation is cheap, so there's nothing to gain from persisting
     * a derivable artifact (see the plan's storage decision).
     */
    public byte[] getQrImage(String bookingId, String customerId) {
        Ticket ticket = ticketRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new TicketNotFoundException(bookingId));
        if (!ticket.getCustomerId().equals(customerId)) {
            throw ForbiddenException.notTicketOwner(bookingId);
        }
        if (ticket.getStatus() == Ticket.Status.REVOKED) {
            throw new TicketRevokedException(bookingId);
        }
        try {
            return qrCodeGenerator.generate(ticket.getQrContent(), QR_IMAGE_SIZE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate QR image for booking " + bookingId, e);
        }
    }
}
