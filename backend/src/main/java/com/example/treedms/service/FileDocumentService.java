package com.example.treedms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.treedms.config.FileStorageProperties;
import com.example.treedms.dto.FileItemResponse;
import com.example.treedms.dto.SessionUser;
import com.example.treedms.entity.BusinessFile;
import com.example.treedms.exception.AppException;
import com.example.treedms.mapper.BusinessFileMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileDocumentService {

    private final BusinessFileMapper businessFileMapper;
    private final DepartmentService departmentService;
    private final FileStorageProperties storageProperties;

    public FileDocumentService(
            BusinessFileMapper businessFileMapper,
            DepartmentService departmentService,
            FileStorageProperties storageProperties) {
        this.businessFileMapper = businessFileMapper;
        this.departmentService = departmentService;
        this.storageProperties = storageProperties;
    }

    @PostConstruct
    public void initializeStorage() throws IOException {
        Files.createDirectories(storageProperties.rootPath());
    }

    public List<FileItemResponse> listByDepartment(Long departmentId, SessionUser user) {
        departmentService.requireVisible(departmentId, user);
        return businessFileMapper.selectList(new LambdaQueryWrapper<BusinessFile>()
                        .eq(BusinessFile::getDeptId, departmentId)
                        .orderByDesc(BusinessFile::getPinned)
                        .orderByAsc(BusinessFile::getSortOrder)
                        .orderByDesc(BusinessFile::getCreatedAt)
                        .orderByDesc(BusinessFile::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public FileItemResponse upload(Long departmentId, MultipartFile file, String uploader) {
        departmentService.requireExists(departmentId);
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }

        String originalName = cleanOriginalName(file.getOriginalFilename());
        String storageName = UUID.randomUUID() + extensionOf(originalName);
        String relativePath = departmentId + "/" + LocalDate.now() + "/" + storageName;
        Path destination = resolveForWrite(relativePath);

        try (InputStream inputStream = file.getInputStream()) {
            Files.createDirectories(destination.getParent());
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败: " + ex.getMessage());
        }

        BusinessFile entity = new BusinessFile();
        entity.setDeptId(departmentId);
        entity.setOriginalName(originalName);
        entity.setStorageName(storageName);
        entity.setRelativePath(relativePath.replace('\\', '/'));
        entity.setContentType(StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream");
        entity.setSize(file.getSize());
        entity.setUploader(uploader);
        entity.setPinned(0);
        entity.setSortOrder(nextSortOrder(departmentId));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        businessFileMapper.insert(entity);
        return toResponse(entity);
    }

    public void delete(Long id) {
        BusinessFile file = findActive(id);
        file.setUpdatedAt(LocalDateTime.now());
        businessFileMapper.deleteById(id);
    }

    public List<FileItemResponse> listDeleted() {
        return businessFileMapper.selectDeletedFiles()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public FileItemResponse move(Long id, Long departmentId) {
        departmentService.requireExists(departmentId);
        BusinessFile file = findActive(id);
        file.setDeptId(departmentId);
        file.setSortOrder(nextSortOrder(departmentId));
        file.setUpdatedAt(LocalDateTime.now());
        businessFileMapper.updateById(file);
        return toResponse(file);
    }

    public FileItemResponse restore(Long id) {
        BusinessFile file = businessFileMapper.selectDeletedById(id);
        if (file == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "回收站文件不存在");
        }
        departmentService.requireExists(file.getDeptId());
        Path path = resolveExisting(file);
        if (!Files.exists(path)) {
            throw new AppException(HttpStatus.NOT_FOUND, "磁盘文件不存在，无法恢复");
        }

        int updated = businessFileMapper.restoreDeletedById(id);
        if (updated == 0) {
            throw new AppException(HttpStatus.NOT_FOUND, "回收站文件不存在");
        }
        file.setDeleted(0);
        file.setUpdatedAt(LocalDateTime.now());
        return toResponse(file);
    }

    public FileItemResponse setPinned(Long id, boolean pinned) {
        BusinessFile file = findActive(id);
        file.setPinned(pinned ? 1 : 0);
        file.setUpdatedAt(LocalDateTime.now());
        businessFileMapper.updateById(file);
        return toResponse(file);
    }

    public StoredFileResource open(Long id, SessionUser user) {
        BusinessFile file = findActive(id);
        departmentService.requireVisible(file.getDeptId(), user);
        Path path = resolveExisting(file);
        return new StoredFileResource(file, new FileSystemResource(path));
    }

    private BusinessFile findActive(Long id) {
        BusinessFile file = businessFileMapper.selectById(id);
        if (file == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        return file;
    }

    private FileItemResponse toResponse(BusinessFile file) {
        return new FileItemResponse(
                file.getId(),
                file.getDeptId(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                file.getUploader(),
                file.getCreatedAt(),
                Integer.valueOf(1).equals(file.getPinned()),
                file.getSortOrder());
    }

    private String cleanOriginalName(String originalFilename) {
        String name = StringUtils.cleanPath(StringUtils.hasText(originalFilename) ? originalFilename : "unnamed");
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "文件名不合法");
        }
        return name;
    }

    private String extensionOf(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index);
    }

    private Path resolveForWrite(String relativePath) {
        Path root = storageProperties.rootPath();
        Path destination = root.resolve(relativePath).normalize();
        if (!destination.startsWith(root)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "文件路径不合法");
        }
        return destination;
    }

    private Path resolveExisting(BusinessFile file) {
        Path path = resolveForWrite(file.getRelativePath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new AppException(HttpStatus.NOT_FOUND, "磁盘文件不存在");
        }
        return path;
    }

    private Integer nextSortOrder(Long departmentId) {
        List<Object> values = businessFileMapper.selectObjs(new QueryWrapper<BusinessFile>()
                .select("COALESCE(MAX(sort_order), 0) + 1000")
                .eq("dept_id", departmentId));
        if (values.isEmpty() || values.get(0) == null) {
            return 1000;
        }
        Object value = values.get(0);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
