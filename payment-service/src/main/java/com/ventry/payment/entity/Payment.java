package com.ventry.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private String paymentId;

    @Column(nullable = false)
    private String bookingId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String gatewayUsed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Instant processedAt;

    public enum Status { SUCCESS, FAILED }

    protected Payment() {
    }

    public Payment(String bookingId, BigDecimal amount, String gatewayUsed, Status status) {
        this.paymentId = UUID.randomUUID().toString();
        this.bookingId = bookingId;
        this.amount = amount;
        this.gatewayUsed = gatewayUsed;
        this.status = status;
        this.processedAt = Instant.now();
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getGatewayUsed() {
        return gatewayUsed;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
