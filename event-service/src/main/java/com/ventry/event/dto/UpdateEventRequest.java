package com.ventry.event.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateEventRequest(
        @NotBlank String name,
        String description,
        @NotNull @Future LocalDateTime eventDate,
        @NotBlank String venue,
        String bannerUrl
) {}
