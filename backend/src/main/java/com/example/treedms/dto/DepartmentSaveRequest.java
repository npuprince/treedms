package com.example.treedms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentSaveRequest(
        @NotBlank(message = "不能为空")
        @Size(max = 100, message = "长度不能超过100个字符")
        String department
) {
}
