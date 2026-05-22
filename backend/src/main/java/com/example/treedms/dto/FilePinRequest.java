package com.example.treedms.dto;

import jakarta.validation.constraints.NotNull;

public record FilePinRequest(
        @NotNull Boolean pinned
) {
}
