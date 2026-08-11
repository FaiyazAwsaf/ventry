package com.ventry.common.events;

public record PaymentFailedEvent(
        String bookingId,
        String customerId,
        String reason
) {}
