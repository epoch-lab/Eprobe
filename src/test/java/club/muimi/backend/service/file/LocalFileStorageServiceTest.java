package club.muimi.backend.service.file;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.StoredFilePurpose;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.config.FileStorageProperties;
import club.muimi.backend.config.TaskModuleProperties;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.exception.ValidationException;
import club.muimi.backend.repository.FileUploadSessionRepository;
import club.muimi.backend.repository.StoredFileRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.user.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileUploadSessionRepository fileUploadSessionRepository;
    @Mock
    private CurrentUserService currentUserService;

    private LocalFileStorageService localFileStorageService;

    @BeforeEach
    void setUp() {
        FileStorageProperties fileStorageProperties = new FileStorageProperties();
        fileStorageProperties.setRootPath(tempDir.toString());
        fileStorageProperties.setChunkSize(DataSize.ofBytes(0));
        fileStorageProperties.setAllowedExtensions(java.util.List.of("pdf", "txt"));
        fileStorageProperties.setAllowedContentTypes(java.util.List.of("application/pdf", "text/plain"));
        fileStorageProperties.validate();

        TaskModuleProperties taskModuleProperties = new TaskModuleProperties();
        taskModuleProperties.setAttachmentMaxSize(DataSize.ofMegabytes(20));
        taskModuleProperties.validate();

        localFileStorageService = new LocalFileStorageService(
                fileStorageProperties,
                taskModuleProperties,
                storedFileRepository,
                fileUploadSessionRepository,
                currentUserService
        );
    }

    @Test
    void uploadDirectShouldRejectDisallowedExtension() {
        when(currentUserService.requireCurrentUser()).thenReturn(buildLoginUser());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.exe",
                "application/octet-stream",
                "hello".getBytes()
        );

        assertThatThrownBy(() -> localFileStorageService.uploadDirect(StoredFilePurpose.MATERIAL_ATTACHMENT, file))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("当前角色无权上传该类型附件");
    }

    @Test
    void uploadDirectShouldRejectDisallowedExtensionForAllowedPurpose() {
        when(currentUserService.requireCurrentUser()).thenReturn(buildLoginUser());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.exe",
                "application/octet-stream",
                "hello".getBytes()
        );

        assertThatThrownBy(() -> localFileStorageService.uploadDirect(StoredFilePurpose.TASK_SUBMISSION_ATTACHMENT, file))
                .isInstanceOf(ValidationException.class)
                .hasMessage("当前文件扩展名不受支持");
    }

    @Test
    void uploadDirectShouldRejectTaskAttachmentFromFreshman() {
        when(currentUserService.requireCurrentUser()).thenReturn(buildLoginUser());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "task.txt",
                "text/plain",
                "hello".getBytes()
        );

        assertThatThrownBy(() -> localFileStorageService.uploadDirect(StoredFilePurpose.TASK_ATTACHMENT, file))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("当前角色无权上传该类型附件");
    }

    @Test
    void uploadDirectShouldRejectDisallowedContentType() {
        when(currentUserService.requireCurrentUser()).thenReturn(buildLoginUser());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "doc.txt",
                "application/json",
                "hello".getBytes()
        );

        assertThatThrownBy(() -> localFileStorageService.uploadDirect(StoredFilePurpose.TASK_SUBMISSION_ATTACHMENT, file))
                .isInstanceOf(ValidationException.class)
                .hasMessage("当前文件类型不受支持");
    }

    @Test
    void uploadDirectShouldRejectUnsafeExtensionEvenWhenExtensionWhitelistIsEmpty() {
        FileStorageProperties fileStorageProperties = new FileStorageProperties();
        fileStorageProperties.setRootPath(tempDir.toString());
        fileStorageProperties.setChunkSize(DataSize.ofBytes(0));
        fileStorageProperties.setAllowedExtensions(java.util.List.of());
        fileStorageProperties.setAllowedContentTypes(java.util.List.of());
        fileStorageProperties.validate();

        TaskModuleProperties taskModuleProperties = new TaskModuleProperties();
        taskModuleProperties.setAttachmentMaxSize(DataSize.ofMegabytes(20));
        taskModuleProperties.validate();

        LocalFileStorageService permissiveStorageService = new LocalFileStorageService(
                fileStorageProperties,
                taskModuleProperties,
                storedFileRepository,
                fileUploadSessionRepository,
                currentUserService
        );
        when(currentUserService.requireCurrentUser()).thenReturn(buildLoginUser());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payload.txt/../../../outside",
                "text/plain",
                "hello".getBytes()
        );

        assertThatThrownBy(() -> permissiveStorageService.uploadDirect(StoredFilePurpose.TASK_SUBMISSION_ATTACHMENT, file))
                .isInstanceOf(ValidationException.class)
                .hasMessage("当前文件扩展名不受支持");
    }

    @Test
    void uploadDirectShouldRejectBlankOriginalFileName() {
        when(currentUserService.requireCurrentUser()).thenReturn(buildLoginUser());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "   ",
                "text/plain",
                "hello".getBytes()
        );

        assertThatThrownBy(() -> localFileStorageService.uploadDirect(StoredFilePurpose.TASK_SUBMISSION_ATTACHMENT, file))
                .isInstanceOf(ValidationException.class)
                .hasMessage("文件名不能为空");
    }

    @Test
    void uploadDirectShouldRejectTooLongOriginalFileNameBeforeWritingDatabaseRecord() {
        when(currentUserService.requireCurrentUser()).thenReturn(buildLoginUser());
        String fileName = "a".repeat(252) + ".txt";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                "hello".getBytes()
        );

        assertThatThrownBy(() -> localFileStorageService.uploadDirect(StoredFilePurpose.TASK_SUBMISSION_ATTACHMENT, file))
                .isInstanceOf(ValidationException.class)
                .hasMessage("文件名长度不能超过 255 个字符");
    }

    private LoginUser buildLoginUser() {
        return new LoginUser(
                1L,
                "freshman",
                "freshman@example.com",
                "hashed",
                Role.FRESHMAN,
                UserStatus.ACTIVE,
                0L,
                "jti-1"
        );
    }
}
