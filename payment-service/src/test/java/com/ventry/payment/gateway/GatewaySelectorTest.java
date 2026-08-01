package com.ventry.payment.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySelectorTest {

    private final BigDecimal threshold = BigDecimal.valueOf(100000);
    private final GatewaySelector selector =
            new GatewaySelector(new BkashMockGateway(threshold), new SslCommerzMockGateway(threshold));

    @Test
    void selectionIsDeterministicForTheSameBookingId() {
        assertThat(selector.select("booking-2").name()).isEqualTo("BKASH");
        assertThat(selector.select("booking-2").name()).isEqualTo("BKASH");
    }

    @Test
    void bothGatewaysAreReachableAcrossDifferentBookingIds() {
        assertThat(selector.select("booking-2").name()).isEqualTo("BKASH");
        assertThat(selector.select("booking-1").name()).isEqualTo("SSLCOMMERZ");
    }

    @Test
    void negativeHashCodeBookingIdStillMapsToAValidIndex() {
        // "booking-10".hashCode() is -1374610413 - a plain `%` (not Math.floorMod) would
        // yield -1 here and throw IndexOutOfBoundsException on gateways.get(-1).
        assertThat("booking-10".hashCode()).isNegative();
        assertThat(selector.select("booking-10").name()).isEqualTo("SSLCOMMERZ");
    }
}
