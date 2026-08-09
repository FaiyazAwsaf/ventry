package com.ventry.qrticket.exception;

/**
 * Two distinct 403 reasons share this type: a caller lacking the ADMIN role (scan-validation),
 * and a caller fetching a ticket they don't own (QR fetch). Static factories keep each call
 * site self-documenting instead of scattering raw message strings.
 */
public class ForbiddenException extends RuntimeException {

    private ForbiddenException(String message) {
        super(message);
    }

    public static ForbiddenException nonAdminRole(String role) {
        return new ForbiddenException("Role '" + role + "' is not permitted to perform this action; ADMIN required");
    }

    public static ForbiddenException notTicketOwner(String bookingId) {
        return new ForbiddenException("Caller does not own the ticket for booking '" + bookingId + "'");
    }
}
