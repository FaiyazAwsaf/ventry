package com.ventry.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private String notificationId;

    @Column(nullable = false)
    private String bookingId;

    @Column(nullable = false)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(nullable = false)
    private Instant sentAt;

    public enum Type { BOOKING_CONFIRMED, PAYMENT_FAILED, BOOKING_CANCELLED, REFUND_PROCESSED, TICKET_GENERATED }

    public enum Channel { EMAIL, SMS }

    protected Notification() {
    }

    public Notification(String bookingId, String recipientId, Type type, Channel channel, String message) {
        this.notificationId = UUID.randomUUID().toString();
        this.bookingId = bookingId;
        this.recipientId = recipientId;
        this.type = type;
        this.channel = channel;
        this.message = message;
        this.sentAt = Instant.now();
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public Type getType() {
        return type;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getMessage() {
        return message;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
