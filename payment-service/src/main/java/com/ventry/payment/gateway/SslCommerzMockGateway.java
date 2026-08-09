package com.ventry.payment.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Mocked SSLCommerz gateway - same decline-by-threshold simulation as
 * BkashMockGateway. Kept as a separate, near-identical class rather than sharing a
 * base class: the duplication itself is the point here, demonstrating two genuinely
 * distinct implementations behind PaymentGateway's unified interface.
 */
@Component
public class SslCommerzMockGateway implements PaymentGateway {

    private final BigDecimal declineThreshold;

    public SslCommerzMockGateway(@Value("${payment.decline-threshold}") BigDecimal declineThreshold) {
        this.declineThreshold = declineThreshold;
    }

    @Override
    public boolean processPayment(String bookingId, BigDecimal amount) {
        return amount.compareTo(declineThreshold) <= 0;
    }

    @Override
    public void refund(String bookingId, BigDecimal amount) {
        // Mock: a real integration would call SSLCommerz's refund API here.
    }

    @Override
    public String name() {
        return "SSLCOMMERZ";
    }
}
