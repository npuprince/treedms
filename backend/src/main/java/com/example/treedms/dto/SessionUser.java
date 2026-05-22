package com.example.treedms.dto;

public record SessionUser(
        String username,
        UserRole role
) {
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
