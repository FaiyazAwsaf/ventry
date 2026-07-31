package com.ventry.payment.gateway;

import java.math.BigDecimal;

/**
 * Unified interface abstracting both mock payment providers (bKash, SSLCommerz) per
 * architecture.md §3.4 - PaymentService talks to this interface only, never to a
 * concrete gateway, so a real gateway integration could later replace either
 * implementation without touching any calling code.
 */
public interface PaymentGateway {

    boolean processPayment(String bookingId, BigDecimal amount);

    /**
     * Self-reported identity, stored on Payment.gatewayUsed - avoids callers having to
     * reflect on the concrete class (e.g. getClass().getSimpleName()) just to know which
     * provider handled a given payment.
     */
    String name();
}
