package com.ventry.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * customerId is deliberately absent here - it comes from the Gateway-verified X-User-Id
 * header, never from the request body, so a caller can't book on someone else's behalf.
 */
public record CreateBookingRequest(
        @NotBlank String eventId,
        @NotBlank String tierId,
        @Positive int quantity
) {
}
