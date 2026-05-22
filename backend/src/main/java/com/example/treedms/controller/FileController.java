package com.example.treedms.controller;

import com.example.treedms.dto.FileItemResponse;
import com.example.treedms.dto.FileMoveRequest;
import com.example.treedms.dto.FilePinRequest;
import com.example.treedms.dto.FileVersionResponse;
import com.example.treedms.service.AuthService;
import com.example.treedms.service.FileDocumentService;
import com.example.treedms.service.StoredFileResource;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileDocumentService fileDocumentService;
    private final AuthService authService;

    public FileController(FileDocumentService fileDocumentService, AuthService authService) {
        this.fileDocumentService = fileDocumentService;
        this.authService = authService;
    }

    @GetMapping
    public List<FileItemResponse> list(@RequestParam Long departmentId) {
        return fileDocumentService.listByDepartment(departmentId, authService.currentUser());
    }

    @GetMapping("/recycle")
    public List<FileItemResponse> recycleBin() {
        authService.requireAdmin();
        return fileDocumentService.listDeleted();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileItemResponse upload(@RequestParam Long departmentId, @RequestPart("file") MultipartFile file) {
        authService.requireAdmin();
        return fileDocumentService.upload(departmentId, file, authService.currentUser().username());
    }

    @PutMapping("/{id}/move")
    public FileItemResponse move(@PathVariable Long id, @Valid @RequestBody FileMoveRequest request) {
        authService.requireAdmin();
        return fileDocumentService.move(id, request.departmentId());
    }

    @PutMapping("/{id}/pin")
    public FileItemResponse setPinned(@PathVariable Long id, @Valid @RequestBody FilePinRequest request) {
        authService.requireAdmin();
        return fileDocumentService.setPinned(id, request.pinned());
    }

    @PutMapping("/{id}/restore")
    public FileItemResponse restore(@PathVariable Long id) {
        authService.requireAdmin();
        return fileDocumentService.restore(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        authService.requireAdmin();
        fileDocumentService.delete(id);
    }

    @GetMapping("/{id}/versions")
    public List<FileVersionResponse> versions(@PathVariable Long id) {
        return fileDocumentService.versions(id, authService.currentUser());
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        return fileResponse(fileDocumentService.open(id, authService.currentUser()), true);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        return fileResponse(fileDocumentService.open(id, authService.currentUser()), false);
    }

    @GetMapping("/{id}/versions/{versionId}/preview")
    public ResponseEntity<Resource> previewVersion(@PathVariable Long id, @PathVariable Long versionId) {
        return fileResponse(fileDocumentService.openVersion(id, versionId, authService.currentUser()), true);
    }

    @GetMapping("/{id}/versions/{versionId}/download")
    public ResponseEntity<Resource> downloadVersion(@PathVariable Long id, @PathVariable Long versionId) {
        return fileResponse(fileDocumentService.openVersion(id, versionId, authService.currentUser()), false);
    }

    private ResponseEntity<Resource> fileResponse(StoredFileResource storedFile, boolean inline) {
        Resource resource = storedFile.resource();
        String dispositionType = inline ? "inline" : "attachment";
        ContentDisposition disposition = ContentDisposition.builder(dispositionType)
                .filename(storedFile.filename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(parseMediaType(storedFile.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    private MediaType parseMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
