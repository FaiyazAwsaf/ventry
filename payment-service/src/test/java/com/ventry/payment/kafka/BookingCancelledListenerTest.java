package com.ventry.payment.kafka;

import com.ventry.common.events.BookingCancelledEvent;
import com.ventry.common.events.RefundProcessedEvent;
import com.ventry.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCancelledListenerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private BookingCancelledListener listener;

    @Test
    void handleBookingCancelled_publishesRefundProcessedWhenRefundSucceeds() {
        BookingCancelledEvent event = new BookingCancelledEvent("booking-1", "customer-1", BigDecimal.valueOf(1000));
        when(paymentService.processRefund(event)).thenReturn(true);

        listener.handleBookingCancelled(event);

        verify(kafkaTemplate).send(eq("refund.processed"), eq("booking-1"),
                eq(new RefundProcessedEvent("booking-1")));
    }

    @Test
    void handleBookingCancelled_doesNotPublishOnIdempotentNoOp() {
        BookingCancelledEvent event = new BookingCancelledEvent("booking-1", "customer-1", BigDecimal.valueOf(1000));
        when(paymentService.processRefund(event)).thenReturn(false);

        listener.handleBookingCancelled(event);

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}
