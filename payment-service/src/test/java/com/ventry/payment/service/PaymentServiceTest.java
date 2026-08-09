package com.ventry.payment.service;

import com.ventry.common.events.BookingCancelledEvent;
import com.ventry.common.events.BookingInitiatedEvent;
import com.ventry.payment.entity.Payment;
import com.ventry.payment.exception.PaymentNotFoundException;
import com.ventry.payment.gateway.GatewaySelector;
import com.ventry.payment.gateway.PaymentGateway;
import com.ventry.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private GatewaySelector gatewaySelector;

    @Mock
    private PaymentGateway gateway;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void processPayment_savesSuccessPaymentWhenGatewayApproves() {
        BookingInitiatedEvent event = new BookingInitiatedEvent(
                "booking-1", "customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));

        when(gatewaySelector.select("booking-1")).thenReturn(gateway);
        when(gateway.processPayment("booking-1", BigDecimal.valueOf(1000))).thenReturn(true);
        when(gateway.name()).thenReturn("BKASH");
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentService.processPayment(event);

        assertThat(payment.getBookingId()).isEqualTo("booking-1");
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(payment.getGatewayUsed()).isEqualTo("BKASH");
        assertThat(payment.getStatus()).isEqualTo(Payment.Status.SUCCESS);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(Payment.Status.SUCCESS);
    }

    @Test
    void processPayment_savesFailedPaymentWhenGatewayDeclines() {
        BookingInitiatedEvent event = new BookingInitiatedEvent(
                "booking-2", "customer-1", "event-1", "tier-1", 1, BigDecimal.valueOf(200000));

        when(gatewaySelector.select("booking-2")).thenReturn(gateway);
        when(gateway.processPayment("booking-2", BigDecimal.valueOf(200000))).thenReturn(false);
        when(gateway.name()).thenReturn("SSLCOMMERZ");
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentService.processPayment(event);

        assertThat(payment.getGatewayUsed()).isEqualTo("SSLCOMMERZ");
        assertThat(payment.getStatus()).isEqualTo(Payment.Status.FAILED);
    }

    @Test
    void processRefund_refundsSuccessPaymentAndReturnsTrue() {
        Payment payment = new Payment("booking-1", BigDecimal.valueOf(1000), "BKASH", Payment.Status.SUCCESS);
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(Optional.of(payment));
        when(gatewaySelector.select("booking-1")).thenReturn(gateway);

        BookingCancelledEvent event = new BookingCancelledEvent("booking-1", "customer-1", BigDecimal.valueOf(1000));
        boolean result = paymentService.processRefund(event);

        assertThat(result).isTrue();
        assertThat(payment.getStatus()).isEqualTo(Payment.Status.REFUNDED);
        verify(gateway).refund("booking-1", BigDecimal.valueOf(1000));
        verify(paymentRepository).save(payment);
    }

    @Test
    void processRefund_isIdempotentOnRedeliveredBookingCancelled() {
        Payment payment = new Payment("booking-1", BigDecimal.valueOf(1000), "BKASH", Payment.Status.SUCCESS);
        payment.refund();
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(Optional.of(payment));

        BookingCancelledEvent event = new BookingCancelledEvent("booking-1", "customer-1", BigDecimal.valueOf(1000));
        boolean result = paymentService.processRefund(event);

        assertThat(result).isFalse();
        verify(gatewaySelector, never()).select(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processRefund_throwsWhenPaymentNotFound() {
        when(paymentRepository.findByBookingId("missing-booking")).thenReturn(Optional.empty());

        BookingCancelledEvent event = new BookingCancelledEvent("missing-booking", "customer-1", BigDecimal.TEN);

        assertThatThrownBy(() -> paymentService.processRefund(event))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}
