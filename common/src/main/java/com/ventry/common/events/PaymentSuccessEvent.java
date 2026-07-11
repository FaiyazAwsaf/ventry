package com.ventry.common.events;

import java.math.BigDecimal;

public record PaymentSuccessEvent(
        String bookingId,
        BigDecimal amount
) {}
