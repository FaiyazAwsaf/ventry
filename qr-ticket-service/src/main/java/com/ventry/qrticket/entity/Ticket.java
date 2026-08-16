package com.ventry.qrticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    private String ticketId;

    @Column(nullable = false, unique = true)
    private String bookingId;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String tierId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, columnDefinition = "text")
    private String qrContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Instant generatedAt;

    private Instant validatedAt;

    public enum Status { GENERATED, VALIDATED, REVOKED }

    protected Ticket() {
    }

    public Ticket(String bookingId, String eventId, String tierId, String customerId, int quantity, String qrContent) {
        this.ticketId = UUID.randomUUID().toString();
        this.bookingId = bookingId;
        this.eventId = eventId;
        this.tierId = tierId;
        this.customerId = customerId;
        this.quantity = quantity;
        this.qrContent = qrContent;
        this.status = Status.GENERATED;
        this.generatedAt = Instant.now();
    }

    /**
     * Returns false if the ticket was already VALIDATED - a second scan of the same QR is a
     * distinct physical action (someone rescanning, accidentally or maliciously) that callers
     * should reject (409), not silently accept again. Deliberately different from Booking's
     * redelivery-tolerant confirm()/markFailed(): those absorb Kafka's at-least-once delivery of
     * the same internal event, whereas a duplicate scan here is not a duplicate message.
     */
    public boolean validate() {
        if (status != Status.GENERATED) {
            return false;
        }
        status = Status.VALIDATED;
        validatedAt = Instant.now();
        return true;
    }

    /**
     * Idempotent on booking.cancelled redelivery, same reasoning as validate()'s guard - a
     * ticket already REVOKED shouldn't re-trigger downstream work. Revokes from any prior
     * status (including VALIDATED): a booking can still be cancelled after its ticket was
     * scanned, and the ticket should stop being presentable as valid either way.
     */
    public boolean revoke() {
        if (status == Status.REVOKED) {
            return false;
        }
        status = Status.REVOKED;
        return true;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTierId() {
        return tierId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getQrContent() {
        return qrContent;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }
}
