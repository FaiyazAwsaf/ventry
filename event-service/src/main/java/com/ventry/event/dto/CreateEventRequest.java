package com.ventry.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record CreateEventRequest(
        @NotBlank String name,
        String description,
        @NotNull @Future LocalDateTime eventDate,
        @NotBlank String venue,
        String bannerUrl,
        @NotEmpty @Valid List<CreateTierRequest> tiers
) {}
