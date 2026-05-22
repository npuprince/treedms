package com.example.treedms.dto;

import jakarta.validation.constraints.NotNull;

public record FileMoveRequest(
        @NotNull Long departmentId
) {
}
