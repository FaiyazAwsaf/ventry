package com.ventry.payment.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String bookingId) {
        super("No payment found for booking '" + bookingId + "'");
    }
}
