package com.ventry.booking.dto;

import java.math.BigDecimal;

public record BookingResponse(
        String bookingId,
        String customerId,
        String eventId,
        String tierId,
        int quantity,
        BigDecimal totalAmount,
        String status
) {
}
