package com.ventry.qrticket.dto;

public record ValidateTicketResponse(
        String bookingId,
        String eventId,
        String tierId,
        int quantity,
        String status
) {}
