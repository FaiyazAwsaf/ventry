package com.ventry.qrticket.dto;

import jakarta.validation.constraints.NotBlank;

public record ValidateTicketRequest(
        @NotBlank String qrContent
) {}
