package com.ventry.event.dto;

import jakarta.validation.constraints.Positive;

public record InventoryAdjustmentRequest(
        @Positive int quantity
) {}
