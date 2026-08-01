package com.ventry.payment.gateway;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both mock gateways share the same decline-by-threshold contract - one parameterized
 * test proves it for both implementations instead of duplicating the same assertions
 * per class.
 */
class PaymentGatewayThresholdTest {

    private static final BigDecimal THRESHOLD = BigDecimal.valueOf(100000);

    static Stream<Arguments> gateways() {
        return Stream.of(
                Arguments.of(new BkashMockGateway(THRESHOLD), "BKASH"),
                Arguments.of(new SslCommerzMockGateway(THRESHOLD), "SSLCOMMERZ")
        );
    }

    @ParameterizedTest
    @MethodSource("gateways")
    void approvesAmountAtOrBelowThreshold(PaymentGateway gateway, String expectedName) {
        assertThat(gateway.processPayment("booking-1", BigDecimal.valueOf(50000))).isTrue();
        assertThat(gateway.processPayment("booking-1", THRESHOLD)).isTrue();
        assertThat(gateway.name()).isEqualTo(expectedName);
    }

    @ParameterizedTest
    @MethodSource("gateways")
    void declinesAmountAboveThreshold(PaymentGateway gateway, String expectedName) {
        assertThat(gateway.processPayment("booking-1", THRESHOLD.add(BigDecimal.ONE))).isFalse();
        assertThat(gateway.name()).isEqualTo(expectedName);
    }
}
