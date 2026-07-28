package club.muimi.backend.service.group;

import club.muimi.backend.common.enums.ApplicationStatus;
import club.muimi.backend.common.enums.Grade;
import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.entity.Application;
import club.muimi.backend.entity.RecruitmentGroup;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.repository.*;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.notification.NotificationService;
import club.muimi.backend.service.period.PeriodService;
import club.muimi.backend.service.user.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupManagementServiceTest {

    @Mock
    private RecruitmentGroupRepository recruitmentGroupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private DirectionRepository directionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private PeriodService periodService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogService auditLogService;

    private GroupManagementService groupManagementService;

    @BeforeEach
    void setUp() {
        groupManagementService = new GroupManagementService(
                recruitmentGroupRepository,
                groupMemberRepository,
                applicationRepository,
                directionRepository,
                userRepository,
                currentUserService,
                periodService,
                notificationService,
                auditLogService,
                Clock.fixed(Instant.parse("2026-06-28T03:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
    }

    @Test
    void leaderShouldNotAssignApplicationToOtherLeadersGroup() {
        LoginUser leader = buildLoginUser(10L, Role.LEADER);
        RecruitmentGroup group = RecruitmentGroup.builder()
                .id(20L)
                .leaderUserId(99L)
                .directionLevel1Id(1L)
                .directionLevel2Id(2L)
                .grade(Grade.YEAR_1)
                .admissionYear(2026)
                .maxSize(10)
                .build();

        when(currentUserService.requireCurrentUser()).thenReturn(leader);
        doNothing().when(periodService).ensureSelectionOpenForGrouping();
        when(recruitmentGroupRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupManagementService.assignApplicationToGroup(20L, 30L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("无权操作该分组");
    }

    @Test
    void assignApplicationShouldRejectWhenApplicationDoesNotMatchGroup() {
        LoginUser admin = buildLoginUser(1L, Role.ADMIN);
        RecruitmentGroup group = RecruitmentGroup.builder()
                .id(20L)
                .leaderUserId(99L)
                .directionLevel1Id(1L)
                .directionLevel2Id(2L)
                .grade(Grade.YEAR_1)
                .admissionYear(2026)
                .maxSize(10)
                .build();
        Application application = Application.builder()
                .id(30L)
                .userId(7L)
                .status(ApplicationStatus.SUBMITTED)
                .directionLevel1Id(1L)
                .directionLevel2Id(3L)
                .grade(Grade.YEAR_1)
                .admissionYear(2026)
                .build();

        when(currentUserService.requireCurrentUser()).thenReturn(admin);
        doNothing().when(periodService).ensureSelectionOpenForGrouping();
        when(recruitmentGroupRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(group));
        when(applicationRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(application));
        when(groupMemberRepository.findByApplicationId(30L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupManagementService.assignApplicationToGroup(20L, 30L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("报名申请与目标分组条件不匹配");
    }

    @Test
    void assignApplicationShouldPersistGroupMemberAndUpdateStatus() {
        LoginUser admin = buildLoginUser(1L, Role.ADMIN);
        RecruitmentGroup group = RecruitmentGroup.builder()
                .id(20L)
                .leaderUserId(99L)
                .directionLevel1Id(1L)
                .directionLevel2Id(2L)
                .grade(Grade.YEAR_1)
                .admissionYear(2026)
                .maxSize(10)
                .build();
        Application application = Application.builder()
                .id(30L)
                .userId(7L)
                .status(ApplicationStatus.SUBMITTED)
                .directionLevel1Id(1L)
                .directionLevel2Id(2L)
                .grade(Grade.YEAR_1)
                .admissionYear(2026)
                .build();

        when(currentUserService.requireCurrentUser()).thenReturn(admin);
        doNothing().when(periodService).ensureSelectionOpenForGrouping();
        when(recruitmentGroupRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(group));
        when(applicationRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(application));
        when(groupMemberRepository.findByApplicationId(30L)).thenReturn(Optional.empty());
        when(groupMemberRepository.countByGroupIdForUpdate(20L)).thenReturn(5L);

        groupManagementService.assignApplicationToGroup(20L, 30L);

        ArgumentCaptor<club.muimi.backend.entity.GroupMember> groupMemberCaptor =
                ArgumentCaptor.forClass(club.muimi.backend.entity.GroupMember.class);
        verify(groupMemberRepository).save(groupMemberCaptor.capture());
        assertThat(groupMemberCaptor.getValue().getGroupId()).isEqualTo(20L);
        assertThat(groupMemberCaptor.getValue().getApplicationId()).isEqualTo(30L);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.GROUPED);
        verify(applicationRepository).save(application);
    }

    private LoginUser buildLoginUser(Long userId, Role role) {
        return new LoginUser(
                userId,
                "tester",
                "tester@example.com",
                "hashed",
                role,
                UserStatus.ACTIVE,
                0L,
                "jti"
        );
    }
}
