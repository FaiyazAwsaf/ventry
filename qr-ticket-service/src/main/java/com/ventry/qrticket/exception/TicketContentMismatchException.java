package com.ventry.qrticket.exception;

/**
 * Covers both a scanned qrContent that fails to parse and one that parses but doesn't match the
 * stored ticket's content exactly - a single message either way ("this QR isn't valid") since
 * neither case gives a caller anything more specific to act on.
 */
public class TicketContentMismatchException extends RuntimeException {
    public TicketContentMismatchException() {
        super("Scanned QR content is malformed or does not match the ticket on record");
    }
}
