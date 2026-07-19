package com.ventry.event.dto;

import java.time.LocalDateTime;
import java.util.List;

public record EventResponse(
        String id,
        String name,
        String description,
        LocalDateTime eventDate,
        String venue,
        String bannerUrl,
        List<TierResponse> tiers
) {}
