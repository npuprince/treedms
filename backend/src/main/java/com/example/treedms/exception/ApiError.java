package com.example.treedms.exception;

import java.time.LocalDateTime;

public record ApiError(
        int status,
        String message,
        LocalDateTime timestamp
) {
}
