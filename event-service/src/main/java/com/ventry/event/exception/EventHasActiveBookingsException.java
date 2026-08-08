package com.ventry.event.exception;

public class EventHasActiveBookingsException extends RuntimeException {
    public EventHasActiveBookingsException(String eventId) {
        super("Event '" + eventId + "' has tiers with reserved or sold tickets and cannot be deleted");
    }
}
