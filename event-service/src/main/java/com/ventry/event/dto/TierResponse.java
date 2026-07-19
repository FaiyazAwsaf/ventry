package com.ventry.event.dto;

import java.math.BigDecimal;

public record TierResponse(
        String id,
        String name,
        BigDecimal price,
        int capacity,
        int available
) {}
