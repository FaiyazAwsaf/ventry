package com.ventry.event.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String role) {
        super("Role '" + role + "' is not permitted to perform this action; ADMIN required");
    }
}
