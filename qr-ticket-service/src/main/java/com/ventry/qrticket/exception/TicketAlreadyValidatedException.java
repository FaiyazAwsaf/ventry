package com.ventry.qrticket.exception;

public class TicketAlreadyValidatedException extends RuntimeException {
    public TicketAlreadyValidatedException(String bookingId) {
        super("Ticket for booking '" + bookingId + "' has already been validated");
    }
}
