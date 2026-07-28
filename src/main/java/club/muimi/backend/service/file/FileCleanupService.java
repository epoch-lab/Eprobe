package club.muimi.backend.service.file;

import club.muimi.backend.common.enums.AuditModule;
import club.muimi.backend.common.enums.AuditSeverity;
import club.muimi.backend.config.FileStorageProperties;
import club.muimi.backend.entity.FileUploadSession;
import club.muimi.backend.entity.StoredFile;
import club.muimi.backend.repository.FileUploadSessionRepository;
import club.muimi.backend.repository.StoredFileRepository;
import club.muimi.backend.service.audit.AuditLogCommand;
import club.muimi.backend.service.audit.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FileCleanupService {

    private final FileStorageProperties fileStorageProperties;
    private final FileUploadSessionRepository fileUploadSessionRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final Clock appClock;

    public FileCleanupService(
            FileStorageProperties fileStorageProperties,
            FileUploadSessionRepository fileUploadSessionRepository,
            StoredFileRepository storedFileRepository,
            FileStorageService fileStorageService,
            AuditLogService auditLogService,
            Clock appClock
    ) {
        this.fileStorageProperties = fileStorageProperties;
        this.fileUploadSessionRepository = fileUploadSessionRepository;
        this.storedFileRepository = storedFileRepository;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
        this.appClock = appClock;
    }

    @Scheduled(fixedDelayString = "PT30M")
    @Transactional
    public void cleanup() {
        if (!fileStorageProperties.isCleanupEnabled()) {
            return;
        }
        cleanupExpiredUploadSessions();
        cleanupOrphanStoredFiles();
    }

    private void cleanupExpiredUploadSessions() {
        LocalDateTime threshold = LocalDateTime.now(appClock).minus(fileStorageProperties.getTempSessionTtl());
        List<FileUploadSession> sessions = fileUploadSessionRepository.findAllByUpdatedAtBefore(threshold);
        if (sessions.isEmpty()) {
            return;
        }
        Path root = Path.of(fileStorageProperties.getRootPath()).toAbsolutePath().normalize();
        List<FileUploadSession> cleanupSucceededSessions = new ArrayList<>();
        for (FileUploadSession session : sessions) {
            try {
                Path tempPath = root.resolve(session.getTempStoragePath()).normalize();
                if (!tempPath.startsWith(root)) {
                    throw new IllegalStateException("上传临时文件路径超出存储根目录");
                }
                Files.deleteIfExists(tempPath);
                cleanupSucceededSessions.add(session);
            } catch (Exception exception) {
                log.warn("删除过期上传分片失败，sessionId={}", session.getId(), exception);
                recordCleanupFailure("DELETE_EXPIRED_UPLOAD_SESSION_FAILED", "UPLOAD_SESSION", session.getId(), exception);
            }
        }
        if (!cleanupSucceededSessions.isEmpty()) {
            fileUploadSessionRepository.deleteAll(cleanupSucceededSessions);
        }
    }

    private void cleanupOrphanStoredFiles() {
        LocalDateTime threshold = LocalDateTime.now(appClock).minus(fileStorageProperties.getOrphanFileTtl());
        List<StoredFile> files = storedFileRepository.findAllByBindingTypeIsNullAndBindingIdIsNullAndCreatedAtBefore(threshold);
        for (StoredFile file : files) {
            try {
                fileStorageService.deleteStoredFile(file);
            } catch (Exception exception) {
                log.warn("删除孤儿文件失败，fileId={}", file.getId(), exception);
                recordCleanupFailure("DELETE_ORPHAN_FILE_FAILED", "STORED_FILE", file.getId(), exception);
            }
        }
    }

    private void recordCleanupFailure(String action, String targetType, Long targetId, Exception exception) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetType", targetType);
        detail.put("targetId", targetId);
        detail.put("message", exception.getMessage());
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.FILE,
                        action,
                        AuditSeverity.MAJOR,
                        "文件清理失败"
                ).target(targetType, targetId)
                .success(false)
                .detail(detail)
                .build());
    }
}
