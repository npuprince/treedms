package com.example.treedms.dto;

import java.time.LocalDateTime;

public record FileVersionResponse(
        Long id,
        Long fileId,
        Integer versionNo,
        String contentType,
        Long size,
        String uploader,
        LocalDateTime createdAt,
        Boolean current
) {
}
