package com.ventry.payment.service;

import com.ventry.common.events.BookingInitiatedEvent;
import com.ventry.payment.entity.Payment;
import com.ventry.payment.gateway.GatewaySelector;
import com.ventry.payment.gateway.PaymentGateway;
import com.ventry.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final GatewaySelector gatewaySelector;
    private final PaymentRepository paymentRepository;

    public PaymentService(GatewaySelector gatewaySelector, PaymentRepository paymentRepository) {
        this.gatewaySelector = gatewaySelector;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Runs the booking through its deterministically-selected mock gateway and persists
     * the outcome. The returned Payment's status is the single source of truth the
     * Kafka listener uses to decide whether to publish payment.success or payment.failed.
     */
    public Payment processPayment(BookingInitiatedEvent event) {
        PaymentGateway gateway = gatewaySelector.select(event.bookingId());
        boolean success = gateway.processPayment(event.bookingId(), event.totalAmount());

        Payment payment = new Payment(
                event.bookingId(),
                event.totalAmount(),
                gateway.name(),
                success ? Payment.Status.SUCCESS : Payment.Status.FAILED
        );

        return paymentRepository.save(payment);
    }
}
