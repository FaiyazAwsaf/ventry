package com.ventry.booking.exception;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(String bookingId) {
        super("Booking '" + bookingId + "' not found");
    }
}
