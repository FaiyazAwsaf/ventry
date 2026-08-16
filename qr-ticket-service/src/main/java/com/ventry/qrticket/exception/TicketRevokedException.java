package com.ventry.qrticket.exception;

public class TicketRevokedException extends RuntimeException {
    public TicketRevokedException(String bookingId) {
        super("Ticket for booking '" + bookingId + "' was revoked - the booking was cancelled");
    }
}
