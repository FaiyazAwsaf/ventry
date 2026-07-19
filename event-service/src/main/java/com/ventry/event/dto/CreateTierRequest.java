package com.ventry.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTierRequest(
        @NotBlank String name,
        @NotNull @Positive BigDecimal price,
        @Positive int capacity
) {}
