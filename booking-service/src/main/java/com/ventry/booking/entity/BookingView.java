package com.ventry.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * CQRS read model for a Booking - deliberately denormalized (eventName/tierName captured once
 * at creation via EventServiceClient.getTierDetails, see TierDetails) so GET /bookings/{id} and
 * GET /bookings/history never need a join or a call back to Event Service on every read.
 * Kept in sync with the write side by BookingViewProjector, upserted inside the same
 * transaction as BookingEventStore.append() - never written to directly by BookingService.
 */
@Entity
@Table(name = "booking_view")
public class BookingView {

    @Id
    private String bookingId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String eventName;

    @Column(nullable = false)
    private String tierId;

    @Column(nullable = false)
    private String tierName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Booking.Status status;

    @Column(nullable = false)
    private Instant lastUpdated;

    protected BookingView() {
    }

    public BookingView(String bookingId, String customerId, String eventId, String eventName,
                        String tierId, String tierName, int quantity, BigDecimal totalAmount,
                        Booking.Status status) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.eventId = eventId;
        this.eventName = eventName;
        this.tierId = tierId;
        this.tierName = tierName;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.status = status;
        this.lastUpdated = Instant.now();
    }

    /**
     * The only mutation this view supports - every write-side transition after creation
     * (BOOKING_CONFIRMED, BOOKING_FAILED, ...) only ever changes status, never the
     * denormalized names/amount captured at creation.
     */
    public void updateStatus(Booking.Status status) {
        this.status = status;
        this.lastUpdated = Instant.now();
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

    public String getEventName() {
        return eventName;
    }

    public String getTierId() {
        return tierId;
    }

    public String getTierName() {
        return tierName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Booking.Status getStatus() {
        return status;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }
}
