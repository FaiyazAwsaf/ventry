package com.ventry.payment.kafka;

import com.ventry.common.events.BookingInitiatedEvent;
import com.ventry.common.events.PaymentFailedEvent;
import com.ventry.common.events.PaymentSuccessEvent;
import com.ventry.payment.entity.Payment;
import com.ventry.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingInitiatedListenerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private BookingInitiatedListener listener;

    @Test
    void handleBookingInitiated_publishesPaymentSuccessWhenApproved() {
        BookingInitiatedEvent event = new BookingInitiatedEvent(
                "booking-1", "customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        Payment payment = new Payment("booking-1", BigDecimal.valueOf(1000), "BKASH", Payment.Status.SUCCESS);
        when(paymentService.processPayment(event)).thenReturn(payment);

        listener.handleBookingInitiated(event);

        verify(kafkaTemplate).send(eq("payment.success"), eq("booking-1"),
                eq(new PaymentSuccessEvent("booking-1", BigDecimal.valueOf(1000))));
    }

    @Test
    void handleBookingInitiated_publishesPaymentFailedWhenDeclined() {
        BookingInitiatedEvent event = new BookingInitiatedEvent(
                "booking-2", "customer-1", "event-1", "tier-1", 1, BigDecimal.valueOf(200000));
        Payment payment = new Payment("booking-2", BigDecimal.valueOf(200000), "SSLCOMMERZ", Payment.Status.FAILED);
        when(paymentService.processPayment(event)).thenReturn(payment);

        listener.handleBookingInitiated(event);

        verify(kafkaTemplate).send(eq("payment.failed"), eq("booking-2"),
                eq(new PaymentFailedEvent("booking-2", "customer-1", "Payment declined by SSLCOMMERZ")));
    }
}
