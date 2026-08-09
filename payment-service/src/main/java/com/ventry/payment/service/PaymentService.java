package com.ventry.payment.service;

import com.ventry.common.events.BookingCancelledEvent;
import com.ventry.common.events.BookingInitiatedEvent;
import com.ventry.payment.entity.Payment;
import com.ventry.payment.exception.PaymentNotFoundException;
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

    /**
     * Booking Service only ever cancels a CONFIRMED booking (architecture.md §3.5), so a
     * missing payment record here is an invariant violation, not a normal branch -
     * PaymentNotFoundException is allowed to propagate rather than being swallowed.
     *
     * gatewaySelector.select(bookingId) is called again rather than looking up the stored
     * gatewayUsed - it's a pure function of bookingId (documented on GatewaySelector itself),
     * so re-selecting deterministically returns the exact same gateway the original charge
     * used, with no need to persist/query "which gateway" separately.
     *
     * Returns false on Payment.refund()'s idempotency guard so the caller (the Kafka
     * listener) knows not to publish refund.processed again for a redelivered
     * booking.cancelled.
     */
    public boolean processRefund(BookingCancelledEvent event) {
        Payment payment = paymentRepository.findByBookingId(event.bookingId())
                .orElseThrow(() -> new PaymentNotFoundException(event.bookingId()));

        if (!payment.refund()) {
            return false;
        }

        gatewaySelector.select(event.bookingId()).refund(event.bookingId(), event.totalAmount());
        paymentRepository.save(payment);
        return true;
    }
}
