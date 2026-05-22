package com.example.treedms.dto;

public record LoginResponse(
        String token,
        String username,
        String role
) {
}
