package com.ventry.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only event store: every state transition a Booking goes through gets its own
 * row here, in addition to Booking's own current-state row. Current state = replaying
 * these events in order (see section 6.2); this section only appends them.
 */
@Entity
@Table(name = "booking_event_store")
public class BookingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @Column(nullable = false)
    private String bookingId;

    @Column(nullable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(nullable = false)
    private Instant timestamp;

    protected BookingEvent() {
    }

    public BookingEvent(String bookingId, String eventType, String payload) {
        this.bookingId = bookingId;
        this.eventType = eventType;
        this.payload = payload;
        this.timestamp = Instant.now();
    }

    public Long getEventId() {
        return eventId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
