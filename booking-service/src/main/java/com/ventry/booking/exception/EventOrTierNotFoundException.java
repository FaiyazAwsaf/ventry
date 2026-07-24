package com.ventry.booking.exception;

public class EventOrTierNotFoundException extends RuntimeException {
    public EventOrTierNotFoundException(String eventId, String tierId) {
        super("Event '" + eventId + "' or tier '" + tierId + "' not found");
    }
}
