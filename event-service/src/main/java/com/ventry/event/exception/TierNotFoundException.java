package com.ventry.event.exception;

public class TierNotFoundException extends RuntimeException {
    public TierNotFoundException(String eventId, String tierId) {
        super("No tier '" + tierId + "' found for event '" + eventId + "'");
    }
}
