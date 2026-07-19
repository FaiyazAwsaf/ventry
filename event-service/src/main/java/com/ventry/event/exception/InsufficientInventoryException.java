package com.ventry.event.exception;

public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException(String eventId, String tierId, int requestedQuantity) {
        super("Tier '" + tierId + "' for event '" + eventId + "' does not have "
                + requestedQuantity + " tickets available");
    }
}
