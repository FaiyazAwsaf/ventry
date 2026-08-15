package com.ventry.booking.dto;

import java.math.BigDecimal;

/**
 * The result of literally replaying a booking's booking_event_store history, not a read from
 * BookingView - kept as its own type rather than reusing BookingViewResponse, so it's visibly
 * a different code path producing the answer, not the same read model under another name.
 * eventsReplayed is the actual count of event_store rows folded to produce this result.
 */
public record BookingReplayResponse(
        String bookingId,
        String customerId,
        String eventId,
        String tierId,
        int quantity,
        BigDecimal totalAmount,
        String status,
        int eventsReplayed
) {
}
