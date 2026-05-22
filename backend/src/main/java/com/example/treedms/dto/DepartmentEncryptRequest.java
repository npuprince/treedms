package com.example.treedms.dto;

import jakarta.validation.constraints.NotNull;

public record DepartmentEncryptRequest(
        @NotNull Boolean encrypted
) {
}
