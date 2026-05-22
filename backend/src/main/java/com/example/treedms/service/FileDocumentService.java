package com.example.treedms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.treedms.config.FileStorageProperties;
import com.example.treedms.dto.FileItemResponse;
import com.example.treedms.dto.FileVersionResponse;
import com.example.treedms.dto.SessionUser;
import com.example.treedms.entity.BusinessFile;
import com.example.treedms.entity.FileVersion;
import com.example.treedms.exception.AppException;
import com.example.treedms.mapper.BusinessFileMapper;
import com.example.treedms.mapper.FileVersionMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileDocumentService {

    private final BusinessFileMapper businessFileMapper;
    private final FileVersionMapper fileVersionMapper;
    private final DepartmentService departmentService;
    private final FileStorageProperties storageProperties;

    public FileDocumentService(
            BusinessFileMapper businessFileMapper,
            FileVersionMapper fileVersionMapper,
            DepartmentService departmentService,
            FileStorageProperties storageProperties) {
        this.businessFileMapper = businessFileMapper;
        this.fileVersionMapper = fileVersionMapper;
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
                        .orderByDesc(BusinessFile::getUpdatedAt)
                        .orderByDesc(BusinessFile::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FileItemResponse upload(Long departmentId, MultipartFile file, String uploader) {
        departmentService.requireExists(departmentId);
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }

        String originalName = cleanOriginalName(file.getOriginalFilename());
        BusinessFile existing = findActiveByName(departmentId, originalName);
        SavedUpload saved = saveUpload(departmentId, file, originalName);
        if (existing != null && StringUtils.hasText(existing.getSha256())
                && existing.getSha256().equals(saved.sha256())) {
            deletePhysical(saved.relativePath());
            throw new AppException(HttpStatus.BAD_REQUEST, "文件内容与最新版本一致，无需重复上传");
        }

        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            int versionNo = fileVersionMapper.nextVersionNo(existing.getId());
            fileVersionMapper.insert(toVersion(existing.getId(), versionNo, saved, uploader, now));
            applyLatestVersion(existing, saved, uploader, versionNo, now);
            businessFileMapper.updateById(existing);
            return toResponse(existing);
        }

        BusinessFile entity = new BusinessFile();
        entity.setDeptId(departmentId);
        entity.setOriginalName(originalName);
        entity.setStorageName(saved.storageName());
        entity.setRelativePath(saved.relativePath());
        entity.setContentType(saved.contentType());
        entity.setSize(saved.size());
        entity.setSha256(saved.sha256());
        entity.setUploader(uploader);
        entity.setPinned(0);
        entity.setSortOrder(nextSortOrder(departmentId));
        entity.setVersionNo(1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(0);
        businessFileMapper.insert(entity);
        fileVersionMapper.insert(toVersion(entity.getId(), 1, saved, uploader, now));
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
        BusinessFile sameName = findActiveByName(departmentId, file.getOriginalName());
        if (sameName != null && !sameName.getId().equals(id)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "目标部门已存在同名文件");
        }
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
        BusinessFile sameName = findActiveByName(file.getDeptId(), file.getOriginalName());
        if (sameName != null && !sameName.getId().equals(id)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "原部门已存在同名文件，无法恢复");
        }
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

    public List<FileVersionResponse> versions(Long id, SessionUser user) {
        BusinessFile file = findActive(id);
        departmentService.requireVisible(file.getDeptId(), user);
        Integer currentVersionNo = normalizeVersionNo(file.getVersionNo());
        return fileVersionMapper.selectList(new LambdaQueryWrapper<FileVersion>()
                        .eq(FileVersion::getFileId, id)
                        .orderByDesc(FileVersion::getVersionNo)
                        .orderByDesc(FileVersion::getId))
                .stream()
                .map(version -> toVersionResponse(version, currentVersionNo))
                .toList();
    }

    public StoredFileResource open(Long id, SessionUser user) {
        BusinessFile file = findActive(id);
        departmentService.requireVisible(file.getDeptId(), user);
        Path path = resolveExisting(file);
        return new StoredFileResource(file.getOriginalName(), file.getContentType(), new FileSystemResource(path));
    }

    public StoredFileResource openVersion(Long id, Long versionId, SessionUser user) {
        BusinessFile file = findActive(id);
        departmentService.requireVisible(file.getDeptId(), user);
        FileVersion version = fileVersionMapper.selectById(versionId);
        if (version == null || !id.equals(version.getFileId())) {
            throw new AppException(HttpStatus.NOT_FOUND, "文件版本不存在");
        }
        Path path = resolveExisting(version.getRelativePath());
        return new StoredFileResource(
                versionedName(file.getOriginalName(), version.getVersionNo()),
                version.getContentType(),
                new FileSystemResource(path));
    }

    private BusinessFile findActive(Long id) {
        BusinessFile file = businessFileMapper.selectById(id);
        if (file == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        return file;
    }

    private BusinessFile findActiveByName(Long departmentId, String originalName) {
        return businessFileMapper.selectOne(new LambdaQueryWrapper<BusinessFile>()
                .eq(BusinessFile::getDeptId, departmentId)
                .eq(BusinessFile::getOriginalName, originalName)
                .last("LIMIT 1"));
    }

    private FileItemResponse toResponse(BusinessFile file) {
        int versionNo = normalizeVersionNo(file.getVersionNo());
        int versionCount = Math.max(versionNo, fileVersionMapper.countByFileId(file.getId()));
        return new FileItemResponse(
                file.getId(),
                file.getDeptId(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                file.getUploader(),
                file.getCreatedAt(),
                file.getUpdatedAt(),
                Integer.valueOf(1).equals(file.getPinned()),
                file.getSortOrder(),
                versionNo,
                versionCount);
    }

    private FileVersionResponse toVersionResponse(FileVersion version, Integer currentVersionNo) {
        return new FileVersionResponse(
                version.getId(),
                version.getFileId(),
                version.getVersionNo(),
                version.getContentType(),
                version.getSize(),
                version.getUploader(),
                version.getCreatedAt(),
                currentVersionNo.equals(version.getVersionNo()));
    }

    private void applyLatestVersion(
            BusinessFile file,
            SavedUpload saved,
            String uploader,
            Integer versionNo,
            LocalDateTime updatedAt) {
        file.setStorageName(saved.storageName());
        file.setRelativePath(saved.relativePath());
        file.setContentType(saved.contentType());
        file.setSize(saved.size());
        file.setSha256(saved.sha256());
        file.setUploader(uploader);
        file.setVersionNo(versionNo);
        file.setUpdatedAt(updatedAt);
    }

    private FileVersion toVersion(
            Long fileId,
            Integer versionNo,
            SavedUpload saved,
            String uploader,
            LocalDateTime createdAt) {
        FileVersion version = new FileVersion();
        version.setFileId(fileId);
        version.setVersionNo(versionNo);
        version.setStorageName(saved.storageName());
        version.setRelativePath(saved.relativePath());
        version.setContentType(saved.contentType());
        version.setSize(saved.size());
        version.setSha256(saved.sha256());
        version.setUploader(uploader);
        version.setCreatedAt(createdAt);
        return version;
    }

    private SavedUpload saveUpload(Long departmentId, MultipartFile file, String originalName) {
        String storageName = UUID.randomUUID() + extensionOf(originalName);
        String relativePath = departmentId + "/" + LocalDate.now() + "/" + storageName;
        Path destination = resolveForWrite(relativePath);
        MessageDigest digest = sha256Digest();

        try (InputStream inputStream = file.getInputStream();
                DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            Files.createDirectories(destination.getParent());
            Files.copy(digestInputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败: " + ex.getMessage());
        }

        return new SavedUpload(
                storageName,
                relativePath.replace('\\', '/'),
                StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream",
                file.getSize(),
                HexFormat.of().formatHex(digest.digest()));
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "SHA-256 算法不可用");
        }
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

    private String versionedName(String filename, Integer versionNo) {
        String suffix = "-v" + normalizeVersionNo(versionNo);
        int index = filename.lastIndexOf('.');
        if (index <= 0) {
            return filename + suffix;
        }
        return filename.substring(0, index) + suffix + filename.substring(index);
    }

    private Integer normalizeVersionNo(Integer versionNo) {
        return versionNo == null || versionNo < 1 ? 1 : versionNo;
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
        return resolveExisting(file.getRelativePath());
    }

    private Path resolveExisting(String relativePath) {
        Path path = resolveForWrite(relativePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new AppException(HttpStatus.NOT_FOUND, "磁盘文件不存在");
        }
        return path;
    }

    private void deletePhysical(String relativePath) {
        try {
            Files.deleteIfExists(resolveForWrite(relativePath));
        } catch (IOException ignored) {
            // The database state is still correct when cleanup of a rejected duplicate fails.
        }
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

    private record SavedUpload(
            String storageName,
            String relativePath,
            String contentType,
            Long size,
            String sha256) {
    }
}
