package club.muimi.backend.service.file;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.StoredFilePurpose;
import club.muimi.backend.config.FileStorageProperties;
import club.muimi.backend.config.TaskModuleProperties;
import club.muimi.backend.dto.file.CreateUploadSessionRequest;
import club.muimi.backend.entity.FileUploadSession;
import club.muimi.backend.entity.StoredFile;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.exception.NotFoundException;
import club.muimi.backend.exception.ValidationException;
import club.muimi.backend.repository.FileUploadSessionRepository;
import club.muimi.backend.repository.StoredFileRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.vo.file.StoredFileVo;
import club.muimi.backend.vo.file.UploadSessionVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final String BINDING_TYPE_TASK = "TASK";
    private static final String BINDING_TYPE_TASK_SUBMISSION = "TASK_SUBMISSION";
    private static final String BINDING_TYPE_MATERIAL = "MATERIAL";
    private static final int MAX_ORIGINAL_FILE_NAME_LENGTH = 255;

    private final FileStorageProperties fileStorageProperties;
    private final TaskModuleProperties taskModuleProperties;
    private final StoredFileRepository storedFileRepository;
    private final FileUploadSessionRepository fileUploadSessionRepository;
    private final CurrentUserService currentUserService;

    public LocalFileStorageService(
            FileStorageProperties fileStorageProperties,
            TaskModuleProperties taskModuleProperties,
            StoredFileRepository storedFileRepository,
            FileUploadSessionRepository fileUploadSessionRepository,
            CurrentUserService currentUserService
    ) {
        this.fileStorageProperties = fileStorageProperties;
        this.taskModuleProperties = taskModuleProperties;
        this.storedFileRepository = storedFileRepository;
        this.fileUploadSessionRepository = fileUploadSessionRepository;
        this.currentUserService = currentUserService;
    }

    public static String taskBindingType() {
        return BINDING_TYPE_TASK;
    }

    public static String taskSubmissionBindingType() {
        return BINDING_TYPE_TASK_SUBMISSION;
    }

    public static String materialBindingType() {
        return BINDING_TYPE_MATERIAL;
    }

    @Override
    @Transactional
    public StoredFileVo uploadDirect(StoredFilePurpose purpose, MultipartFile file) {
        ensureChunkUploadDisabled();
        LoginUser currentUser = currentUserService.requireCurrentUser();
        ensurePurposeAllowed(currentUser, purpose);
        validateMultipartFile(purpose, file);
        String originalFileName = normalizeOriginalFileName(file.getOriginalFilename());
        StoredFile storedFile = persistNewFile(
                purpose,
                originalFileName,
                file.getContentType(),
                file.getSize(),
                currentUser.getUserId(),
                file
        );
        return toStoredFileVo(storedFile);
    }

    @Override
    @Transactional
    public UploadSessionVo createUploadSession(CreateUploadSessionRequest request) {
        long chunkSize = fileStorageProperties.getChunkSize().toBytes();
        if (chunkSize <= 0) {
            throw new ConflictException("当前未启用分片上传");
        }
        LoginUser currentUser = currentUserService.requireCurrentUser();
        ensurePurposeAllowed(currentUser, request.purpose());
        validateDeclaredFile(request.purpose(), request.fileName(), request.contentType(), request.totalSize());
        String originalFileName = normalizeOriginalFileName(request.fileName());
        String tempPath = "temp/" + UUID.randomUUID() + ".part";
        FileUploadSession session = FileUploadSession.builder()
                .purpose(request.purpose())
                .originalFileName(originalFileName)
                .contentType(normalizeContentType(request.contentType()))
                .totalSize(request.totalSize())
                .chunkSize(chunkSize)
                .tempStoragePath(tempPath)
                .uploaderUserId(currentUser.getUserId())
                .build();
        FileUploadSession saved = fileUploadSessionRepository.save(session);
        return toUploadSessionVo(saved);
    }

    @Override
    @Transactional
    public UploadSessionVo uploadChunk(Long sessionId, int chunkIndex, MultipartFile chunkFile) {
        long configuredChunkSize = fileStorageProperties.getChunkSize().toBytes();
        if (configuredChunkSize <= 0) {
            throw new ConflictException("当前未启用分片上传");
        }
        LoginUser currentUser = currentUserService.requireCurrentUser();
        FileUploadSession session = fileUploadSessionRepository.findByIdAndUploaderUserId(sessionId, currentUser.getUserId())
                .orElseThrow(() -> new NotFoundException("上传会话不存在"));
        if (chunkIndex != session.getNextChunkIndex()) {
            throw new ConflictException("分片顺序不正确，请从当前期望分片继续上传");
        }
        long chunkSize = chunkFile.getSize();
        if (chunkSize <= 0) {
            throw new ValidationException("上传分片不能为空");
        }
        long remainingSize = session.getTotalSize() - session.getReceivedSize();
        if (remainingSize <= 0) {
            throw new ConflictException("当前上传会话已接收完成");
        }
        long allowedChunkSize = Math.min(session.getChunkSize(), remainingSize);
        if (chunkSize > allowedChunkSize) {
            throw new ValidationException("分片大小超过当前允许范围");
        }

        Path tempPath = resolveManagedPath(session.getTempStoragePath());
        ensureParentDirectory(tempPath);
        try (InputStream inputStream = chunkFile.getInputStream()) {
            Files.write(tempPath, inputStream.readAllBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("写入上传分片失败", exception);
        }

        session.setReceivedSize(session.getReceivedSize() + chunkSize);
        session.setNextChunkIndex(session.getNextChunkIndex() + 1);
        FileUploadSession saved = fileUploadSessionRepository.save(session);
        return toUploadSessionVo(saved);
    }

    @Override
    @Transactional
    public StoredFileVo completeUploadSession(Long sessionId) {
        long configuredChunkSize = fileStorageProperties.getChunkSize().toBytes();
        if (configuredChunkSize <= 0) {
            throw new ConflictException("当前未启用分片上传");
        }
        LoginUser currentUser = currentUserService.requireCurrentUser();
        FileUploadSession session = fileUploadSessionRepository.findByIdAndUploaderUserId(sessionId, currentUser.getUserId())
                .orElseThrow(() -> new NotFoundException("上传会话不存在"));
        if (session.getReceivedSize() != session.getTotalSize()) {
            throw new ConflictException("上传尚未完成，不能结束会话");
        }

        Path tempPath = resolveManagedPath(session.getTempStoragePath());
        if (!Files.exists(tempPath)) {
            throw new NotFoundException("上传临时文件不存在");
        }
        String finalRelativePath = buildFinalRelativePath(session.getOriginalFileName());
        Path finalPath = resolveManagedPath(finalRelativePath);
        ensureParentDirectory(finalPath);
        try {
            Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("完成分片上传失败", exception);
        }
        registerMoveRollback(finalPath, tempPath);

        try {
            StoredFile storedFile = storedFileRepository.save(StoredFile.builder()
                    .purpose(session.getPurpose())
                    .originalFileName(session.getOriginalFileName())
                    .contentType(normalizeContentType(session.getContentType()))
                    .sizeBytes(session.getTotalSize())
                    .storagePath(finalRelativePath)
                    .uploaderUserId(session.getUploaderUserId())
                    .build());
            fileUploadSessionRepository.delete(session);
            return toStoredFileVo(storedFile);
        } catch (RuntimeException exception) {
            restoreMovedFile(finalPath, tempPath);
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFile requireOwnedUnboundFile(Long fileId, StoredFilePurpose purpose, Long uploaderUserId) {
        StoredFile storedFile = storedFileRepository.findByIdAndUploaderUserId(fileId, uploaderUserId)
                .orElseThrow(() -> new NotFoundException("附件不存在"));
        if (storedFile.getPurpose() != purpose) {
            throw new ConflictException("附件用途不匹配");
        }
        if (storedFile.getBindingType() != null || storedFile.getBindingId() != null) {
            throw new ConflictException("附件已被其他资源占用");
        }
        return storedFile;
    }

    @Override
    @Transactional
    public void bindFile(StoredFile storedFile, String bindingType, Long bindingId) {
        storedFile.setBindingType(normalizeBindingType(bindingType));
        storedFile.setBindingId(bindingId);
        storedFileRepository.save(storedFile);
    }

    @Override
    @Transactional
    public void deleteStoredFile(StoredFile storedFile) {
        Path path = resolveManagedPath(storedFile.getStoragePath());
        storedFileRepository.delete(storedFile);
        schedulePhysicalDelete(path);
    }

    private void schedulePhysicalDelete(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deletePhysicalFile(path);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deletePhysicalFile(path);
            }
        });
    }

    private void deletePhysicalFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("删除本地附件失败：{}", path, exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadAsResource(StoredFile storedFile) {
        Path path = resolveManagedPath(storedFile.getStoragePath());
        if (!Files.exists(path)) {
            throw new NotFoundException("附件文件不存在");
        }
        return new FileSystemResource(path);
    }

    private void ensureChunkUploadDisabled() {
        if (fileStorageProperties.getChunkSize().toBytes() > 0) {
            throw new ConflictException("当前启用了分片上传，请改用分片上传接口");
        }
    }

    private void validateMultipartFile(StoredFilePurpose purpose, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("上传文件不能为空");
        }
        String originalFileName = normalizeOriginalFileName(file.getOriginalFilename());
        validateDeclaredFile(
                purpose,
                originalFileName,
                file.getContentType(),
                file.getSize()
        );
    }

    private void validateDeclaredFile(StoredFilePurpose purpose, String fileName, String contentType, long totalSize) {
        String normalizedFileName = normalizeOriginalFileName(fileName);
        if (totalSize <= 0) {
            throw new ValidationException("文件大小必须大于 0");
        }
        if ((purpose == StoredFilePurpose.TASK_ATTACHMENT || purpose == StoredFilePurpose.TASK_SUBMISSION_ATTACHMENT)
                && totalSize > taskModuleProperties.getAttachmentMaxSize().toBytes()) {
            throw new ValidationException("任务附件大小超过系统限制");
        }
        validateFileType(normalizedFileName, contentType);
    }

    private StoredFile persistNewFile(
            StoredFilePurpose purpose,
            String originalFileName,
            String contentType,
            long sizeBytes,
            Long uploaderUserId,
            MultipartFile multipartFile
    ) {
        String relativePath = buildFinalRelativePath(originalFileName);
        Path finalPath = resolveManagedPath(relativePath);
        ensureParentDirectory(finalPath);
        try (InputStream inputStream = multipartFile.getInputStream()) {
            Files.copy(inputStream, finalPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("保存上传文件失败", exception);
        }
        registerCreatedFileRollback(finalPath);
        try {
            return storedFileRepository.save(StoredFile.builder()
                    .purpose(purpose)
                    .originalFileName(originalFileName)
                    .contentType(normalizeContentType(contentType))
                    .sizeBytes(sizeBytes)
                    .storagePath(relativePath)
                    .uploaderUserId(uploaderUserId)
                    .build());
        } catch (RuntimeException exception) {
            deletePhysicalFile(finalPath);
            throw exception;
        }
    }

    private String normalizeBindingType(String bindingType) {
        String normalized = bindingType == null ? null : bindingType.trim().toUpperCase(Locale.ROOT);
        if (!BINDING_TYPE_TASK.equals(normalized)
                && !BINDING_TYPE_TASK_SUBMISSION.equals(normalized)
                && !BINDING_TYPE_MATERIAL.equals(normalized)) {
            throw new IllegalArgumentException("未知的文件绑定类型");
        }
        return normalized;
    }

    private String buildFinalRelativePath(String originalFileName) {
        String extension = extractExtension(originalFileName);
        return "files/" + UUID.randomUUID() + (extension == null ? "" : "." + extension);
    }

    private String normalizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new ValidationException("文件名不能为空");
        }
        String normalized = originalFileName.trim();
        if (normalized.length() > MAX_ORIGINAL_FILE_NAME_LENGTH) {
            throw new ValidationException("文件名长度不能超过 255 个字符");
        }
        return normalized;
    }

    private void registerMoveRollback(Path finalPath, Path tempPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                restoreMovedFile(finalPath, tempPath);
            }
        });
    }

    private void registerCreatedFileRollback(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                deletePhysicalFile(path);
            }
        });
    }

    private void restoreMovedFile(Path finalPath, Path tempPath) {
        if (!Files.exists(finalPath)) {
            return;
        }
        try {
            ensureParentDirectory(tempPath);
            Files.move(finalPath, tempPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            log.warn("回滚分片上传最终文件失败：finalPath={}, tempPath={}", finalPath, tempPath, exception);
        }
    }

    private Path resolveRootPath() {
        Path rootPath = Path.of(fileStorageProperties.getRootPath()).toAbsolutePath().normalize();
        ensureParentDirectory(rootPath.resolve("files/.keep"));
        ensureParentDirectory(rootPath.resolve("temp/.keep"));
        return rootPath;
    }

    private void ensureParentDirectory(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new IllegalStateException("创建文件目录失败", exception);
        }
    }

    private Path resolveManagedPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new NotFoundException("附件文件不存在");
        }
        Path rootPath = resolveRootPath();
        Path resolvedPath = rootPath.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(rootPath)) {
            throw new NotFoundException("附件文件不存在");
        }
        return resolvedPath;
    }

    private void ensurePurposeAllowed(LoginUser currentUser, StoredFilePurpose purpose) {
        if ((purpose == StoredFilePurpose.MATERIAL_ATTACHMENT || purpose == StoredFilePurpose.TASK_ATTACHMENT)
                && currentUser.getRole() != Role.ADMIN
                && currentUser.getRole() != Role.LEADER) {
            throw new ForbiddenException("当前角色无权上传该类型附件");
        }
    }

    private void validateFileType(String fileName, String contentType) {
        String extension = extractExtension(fileName);
        if (extension != null && !extension.matches("[a-z0-9]{1,20}")) {
            throw new ValidationException("当前文件扩展名不受支持");
        }
        Set<String> allowedExtensions = normalizeConfiguredValues(fileStorageProperties.getAllowedExtensions());
        if (!allowedExtensions.isEmpty() && (extension == null || !allowedExtensions.contains(extension))) {
            throw new ValidationException("当前文件扩展名不受支持");
        }

        Set<String> allowedContentTypes = normalizeConfiguredValues(fileStorageProperties.getAllowedContentTypes());
        String normalizedContentType = normalizeContentType(contentType);
        if (!allowedContentTypes.isEmpty()
                && normalizedContentType != null
                && !allowedContentTypes.contains(normalizedContentType)) {
            throw new ValidationException("当前文件类型不受支持");
        }
    }

    private Set<String> normalizeConfiguredValues(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private String extractExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index >= fileName.length() - 1) {
            return null;
        }
        return fileName.substring(index + 1).trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int separatorIndex = contentType.indexOf(';');
        String normalized = separatorIndex >= 0 ? contentType.substring(0, separatorIndex) : contentType;
        normalized = normalized.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private StoredFileVo toStoredFileVo(StoredFile storedFile) {
        return new StoredFileVo(
                storedFile.getId(),
                storedFile.getPurpose(),
                storedFile.getOriginalFileName(),
                storedFile.getContentType(),
                storedFile.getSizeBytes()
        );
    }

    private UploadSessionVo toUploadSessionVo(FileUploadSession session) {
        return new UploadSessionVo(
                session.getId(),
                session.getChunkSize(),
                session.getTotalSize(),
                session.getNextChunkIndex()
        );
    }
}
