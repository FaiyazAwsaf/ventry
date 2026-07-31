package com.ventry.payment.gateway;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Picks a gateway deterministically from bookingId's hash, so both mocks actually get
 * exercised across bookings without any client-facing "choose your provider" field.
 *
 * The two gateways are injected by concrete type (not as an interface-typed
 * List<PaymentGateway>, which Spring would populate in a classpath-scan-dependent
 * order that isn't guaranteed stable) and assembled into a fixed-order list here, so
 * index 0 always means bKash and index 1 always means SSLCommerz.
 */
@Component
public class GatewaySelector {

    private final List<PaymentGateway> gateways;

    public GatewaySelector(BkashMockGateway bkashMockGateway, SslCommerzMockGateway sslCommerzMockGateway) {
        this.gateways = List.of(bkashMockGateway, sslCommerzMockGateway);
    }

    public PaymentGateway select(String bookingId) {
        int index = Math.floorMod(bookingId.hashCode(), gateways.size());
        return gateways.get(index);
    }
}
