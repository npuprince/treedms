package com.example.treedms.service;

import com.example.treedms.entity.BusinessFile;
import org.springframework.core.io.Resource;

public record StoredFileResource(
        BusinessFile file,
        Resource resource
) {
}
