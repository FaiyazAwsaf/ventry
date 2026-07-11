package com.ventry.common.events;

import java.math.BigDecimal;

public record BookingConfirmedEvent(
        String bookingId,
        String customerId,
        String eventId,
        String tierId,
        int quantity,
        BigDecimal totalAmount
) {}
