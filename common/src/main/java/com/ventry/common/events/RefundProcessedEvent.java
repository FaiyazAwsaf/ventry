package com.ventry.common.events;

public record RefundProcessedEvent(
        String bookingId,
        String customerId
) {}
