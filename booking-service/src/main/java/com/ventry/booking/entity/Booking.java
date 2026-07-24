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
}
