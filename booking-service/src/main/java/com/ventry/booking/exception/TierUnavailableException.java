package com.ventry.booking.exception;

public class TierUnavailableException extends RuntimeException {
    public TierUnavailableException(String eventId, String tierId, int requestedQuantity) {
        super("Tier '" + tierId + "' for event '" + eventId + "' does not have "
                + requestedQuantity + " tickets available");
    }
}
