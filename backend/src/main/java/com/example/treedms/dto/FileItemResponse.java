package com.example.treedms.dto;

import java.time.LocalDateTime;

public record FileItemResponse(
        Long id,
        Long departmentId,
        String originalName,
        String contentType,
        Long size,
        String uploader,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Boolean pinned,
        Integer sortOrder,
        Integer versionNo,
        Integer versionCount
) {
}
