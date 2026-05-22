package com.example.treedms.service;

import org.springframework.core.io.Resource;

public record StoredFileResource(
        String filename,
        String contentType,
        Resource resource
) {
}
