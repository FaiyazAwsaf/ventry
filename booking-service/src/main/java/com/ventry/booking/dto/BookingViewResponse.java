package com.ventry.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The CQRS query-side response shape - distinct from BookingResponse (the command side's
 * response), since the read model carries fields the write side doesn't (eventName/tierName/
 * lastUpdated). Built from BookingView, never from Booking.
 */
public record BookingViewResponse(
        String bookingId,
        String customerId,
        String eventId,
        String eventName,
        String tierId,
        String tierName,
        int quantity,
        BigDecimal totalAmount,
        String status,
        Instant lastUpdated
) {
}
