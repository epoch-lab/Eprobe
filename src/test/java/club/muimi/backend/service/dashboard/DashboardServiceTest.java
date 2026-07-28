package club.muimi.backend.service.dashboard;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.entity.RecruitmentGroup;
import club.muimi.backend.repository.*;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.task.TaskService;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.vo.dashboard.GroupDashboardSummaryVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private UserRepository userRepository;
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
    private TaskService taskService;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                currentUserService,
                userRepository,
                applicationRepository,
                groupMemberRepository,
                recruitmentGroupRepository,
                recruitmentTaskRepository,
                taskSubmissionRepository,
                taskService
        );
    }

    @Test
    void listManageableGroupSummariesShouldNotQuerySubmissionsWhenGroupsHaveNoTasks() {
        LoginUser admin = new LoginUser(1L, "admin", "admin@example.com", "hashed", Role.ADMIN, UserStatus.ACTIVE, 0L, "jti-admin");
        RecruitmentGroup group = RecruitmentGroup.builder()
                .id(10L)
                .name("后端组")
                .build();
        when(currentUserService.requireCurrentUser()).thenReturn(admin);
        when(recruitmentGroupRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(group));
        when(groupMemberRepository.findAllByGroupIdIn(any())).thenReturn(List.of());
        when(recruitmentTaskRepository.findAllByGroupIdInOrderByCreatedAtDesc(any())).thenReturn(List.of());

        List<GroupDashboardSummaryVo> summaries = dashboardService.listManageableGroupSummaries();

        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().taskCount()).isZero();
        assertThat(summaries.getFirst().submittedCount()).isZero();
        assertThat(summaries.getFirst().reviewedCount()).isZero();
        assertThat(summaries.getFirst().pendingCount()).isZero();
        verify(taskSubmissionRepository, never()).findAllByTaskIdIn(any());
    }
}
