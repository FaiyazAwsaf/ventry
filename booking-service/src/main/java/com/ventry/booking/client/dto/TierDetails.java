package com.ventry.booking.client.dto;

import java.math.BigDecimal;

/**
 * The authoritative price plus the event/tier display names, fetched in the single REST
 * call Booking Service already makes to Event Service at booking-creation time - avoids a
 * second round trip later when the CQRS read model needs these names to denormalize.
 */
public record TierDetails(BigDecimal price, String eventName, String tierName) {
}
