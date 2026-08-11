package com.ventry.common.events;

public record TicketGeneratedEvent(
        String bookingId,
        String customerId
) {}
