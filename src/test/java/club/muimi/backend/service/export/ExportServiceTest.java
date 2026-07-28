package club.muimi.backend.service.export;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.TaskSubmissionStatus;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.entity.*;
import club.muimi.backend.repository.*;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogCommand;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.user.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private RecruitmentGroupRepository recruitmentGroupRepository;
    @Mock
    private RecruitmentTaskRepository recruitmentTaskRepository;
    @Mock
    private TaskSubmissionRepository taskSubmissionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DirectionRepository directionRepository;
    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private AuditLogService auditLogService;

    private ExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new ExportService(
                currentUserService,
                applicationRepository,
                groupMemberRepository,
                recruitmentGroupRepository,
                recruitmentTaskRepository,
                taskSubmissionRepository,
                userRepository,
                directionRepository,
                storedFileRepository,
                auditLogService,
                Clock.systemDefaultZone()
        );
    }

    @Test
    void exportApplicationsShouldReturnWorkbookWhenNoApplications() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        when(currentUserService.requireCurrentUser()).thenReturn(admin);
        when(applicationRepository.findAll()).thenReturn(List.of());

        byte[] bytes = exportService.exportApplications();

        assertThat(bytes).isNotEmpty();
        verify(auditLogService).recordInNewTransaction(any(AuditLogCommand.class));
    }

    @Test
    void exportAdminGroupMembersShouldReturnWorkbookWhenNoGroups() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        when(currentUserService.requireCurrentUser()).thenReturn(admin);
        when(recruitmentGroupRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        byte[] bytes = exportService.exportAdminGroupMembers();

        assertThat(bytes).isNotEmpty();
        verify(auditLogService).recordInNewTransaction(any(AuditLogCommand.class));
    }

    @Test
    void exportManageableGroupTaskResultsShouldReturnWorkbookWhenGroupHasNoTasks() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        RecruitmentGroup group = RecruitmentGroup.builder()
                .id(10L)
                .name("后端组")
                .leaderUserId(2L)
                .build();
        when(currentUserService.requireCurrentUser()).thenReturn(admin);
        when(recruitmentGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(recruitmentTaskRepository.findAllByGroupIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

        byte[] bytes = exportService.exportManageableGroupTaskResults(10L);

        assertThat(bytes).isNotEmpty();
        verify(taskSubmissionRepository, never()).findAllByTaskIdIn(any());
        verify(auditLogService).recordInNewTransaction(any(AuditLogCommand.class));
    }

    @Test
    void exportManageableGroupTaskResultsShouldAllowPendingSubmissionWithoutSubmittedAt() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        RecruitmentGroup group = RecruitmentGroup.builder()
                .id(10L)
                .name("后端组")
                .leaderUserId(2L)
                .build();
        RecruitmentTask task = RecruitmentTask.builder()
                .id(20L)
                .groupId(10L)
                .title("第一周任务")
                .build();
        GroupMember member = GroupMember.builder()
                .id(30L)
                .groupId(10L)
                .userId(40L)
                .applicationId(50L)
                .build();
        TaskSubmission submission = TaskSubmission.builder()
                .id(60L)
                .taskId(20L)
                .userId(40L)
                .status(TaskSubmissionStatus.PENDING)
                .submittedAt(null)
                .build();
        User user = User.builder()
                .id(40L)
                .username("freshman")
                .email("freshman@example.com")
                .build();
        Application application = Application.builder()
                .id(50L)
                .userId(40L)
                .realName("张三")
                .build();

        when(currentUserService.requireCurrentUser()).thenReturn(admin);
        when(recruitmentGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(recruitmentTaskRepository.findAllByGroupIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(task));
        when(taskSubmissionRepository.findAllByTaskIdIn(List.of(20L))).thenReturn(List.of(submission));
        when(groupMemberRepository.findAllByGroupId(10L)).thenReturn(List.of(member));
        when(userRepository.findAllById(any())).thenReturn(List.of(user), List.of());
        when(applicationRepository.findAllById(List.of(50L))).thenReturn(List.of(application));

        byte[] bytes = exportService.exportManageableGroupTaskResults(10L);

        assertThat(bytes).isNotEmpty();
        verify(auditLogService).recordInNewTransaction(any(AuditLogCommand.class));
    }
}
