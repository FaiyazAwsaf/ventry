package com.ventry.event.dto;

public record TierAvailabilityResponse(
        boolean available,
        int remaining
) {}
