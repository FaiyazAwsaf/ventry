package com.ventry.common.events;

import java.math.BigDecimal;

public record BookingCancelledEvent(
        String bookingId,
        String customerId,
        BigDecimal totalAmount
) {}
