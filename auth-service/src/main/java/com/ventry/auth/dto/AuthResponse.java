package com.ventry.auth.dto;

public record AuthResponse(
        String token,
        String email,
        String role
) {}
