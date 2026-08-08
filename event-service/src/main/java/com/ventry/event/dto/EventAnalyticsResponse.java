package com.ventry.event.dto;

import java.math.BigDecimal;
import java.util.List;

public record EventAnalyticsResponse(
        String eventId,
        String eventName,
        int totalCapacity,
        int totalReserved,
        BigDecimal totalRevenue,
        List<TierAnalyticsResponse> tiers
) {}
