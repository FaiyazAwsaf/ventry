package com.ventry.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    private String bookingId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String tierId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    public enum Status { PENDING, CONFIRMED, FAILED, CANCELLATION_PENDING, CANCELLED }

    protected Booking() {
    }

    public Booking(String customerId, String eventId, String tierId, int quantity, BigDecimal totalAmount) {
        this.bookingId = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.eventId = eventId;
        this.tierId = tierId;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.status = Status.PENDING;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTierId() {
        return tierId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Status getStatus() {
        return status;
    }

    /**
     * Idempotent - Kafka's at-least-once delivery (architecture.md's consistency NFR)
     * means payment.success can be redelivered for a booking already CONFIRMED/FAILED.
     * Returns false on a redelivery so the caller knows not to re-append an event-store
     * row or re-publish booking.confirmed.
     */
    public boolean confirm() {
        if (status != Status.PENDING) {
            return false;
        }
        status = Status.CONFIRMED;
        return true;
    }

    /**
     * Same idempotency guard as confirm() - also stops the compensating-transaction
     * inventory release (a REST call, not just a DB write) from firing twice on a
     * redelivered payment.failed.
     */
    public boolean markFailed() {
        if (status != Status.PENDING) {
            return false;
        }
        status = Status.FAILED;
        return true;
    }

    /**
     * Only a CONFIRMED booking can be cancelled (architecture.md §3.5) - a PENDING booking
     * is still mid-Saga with no confirmed payment to refund, and FAILED/CANCELLED are
     * already terminal.
     */
    public boolean cancel() {
        if (status != Status.CONFIRMED) {
            return false;
        }
        status = Status.CANCELLATION_PENDING;
        return true;
    }
}
