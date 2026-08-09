package com.ventry.booking.exception;

public class BookingOwnershipException extends RuntimeException {
    public BookingOwnershipException(String bookingId) {
        super("Caller does not own booking '" + bookingId + "'");
    }
}
