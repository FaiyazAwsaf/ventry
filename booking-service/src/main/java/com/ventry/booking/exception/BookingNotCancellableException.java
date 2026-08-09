package com.ventry.booking.exception;

import com.ventry.booking.entity.Booking;

public class BookingNotCancellableException extends RuntimeException {
    public BookingNotCancellableException(String bookingId, Booking.Status status) {
        super("Booking '" + bookingId + "' cannot be cancelled from status " + status);
    }
}
