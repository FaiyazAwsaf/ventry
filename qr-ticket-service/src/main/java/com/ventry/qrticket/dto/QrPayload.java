package com.ventry.qrticket.dto;

/**
 * The content encoded into the QR image itself - not a REST DTO. Serialized to JSON to build
 * Ticket.qrContent at generation time, and will be deserialized back out of a scanned QR at
 * validation time (see the scan-validation endpoint).
 */
public record QrPayload(
        String bookingId,
        String eventId,
        String tierId,
        String customerId
) {}
