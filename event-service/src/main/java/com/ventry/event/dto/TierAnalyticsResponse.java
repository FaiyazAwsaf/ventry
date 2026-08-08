package com.ventry.event.dto;

import java.math.BigDecimal;

/**
 * reserved/revenue reflect inventory currently held (capacity - available), not confirmed
 * sales - Event Service has no visibility into whether a reservation's payment ever
 * succeeded (that state lives in Booking Service), so a reservation still awaiting payment
 * resolution counts here too.
 */
public record TierAnalyticsResponse(
        String tierId,
        String tierName,
        int capacity,
        int available,
        int reserved,
        BigDecimal revenue
) {}
