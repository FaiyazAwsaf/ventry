package com.ventry.qrticket.exception;

public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(String bookingId) {
        super("No ticket found for booking '" + bookingId + "'");
    }
}
