package com.ventry.payment.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Mocked bKash gateway: declines any payment whose amount exceeds the configured
 * threshold, simulating a real gateway's per-transaction limit. Deterministic by
 * design (not random) so the failure / compensating-transaction path is reproducible
 * on demand, both in tests and in a live demo.
 */
@Component
public class BkashMockGateway implements PaymentGateway {

    private final BigDecimal declineThreshold;

    public BkashMockGateway(@Value("${payment.decline-threshold}") BigDecimal declineThreshold) {
        this.declineThreshold = declineThreshold;
    }

    @Override
    public boolean processPayment(String bookingId, BigDecimal amount) {
        return amount.compareTo(declineThreshold) <= 0;
    }

    @Override
    public void refund(String bookingId, BigDecimal amount) {
        // Mock: a real integration would call bKash's refund API here.
    }

    @Override
    public String name() {
        return "BKASH";
    }
}
