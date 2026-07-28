package club.muimi.backend.service.application;

import club.muimi.backend.common.enums.ApplicationStatus;
import club.muimi.backend.common.enums.Grade;
import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.config.RecruitmentApplicationProperties;
import club.muimi.backend.dto.application.UpsertApplicationRequest;
import club.muimi.backend.entity.Application;
import club.muimi.backend.entity.Direction;
import club.muimi.backend.entity.GroupMember;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.ValidationException;
import club.muimi.backend.repository.ApplicationRepository;
import club.muimi.backend.repository.DirectionRepository;
import club.muimi.backend.repository.GroupMemberRepository;
import club.muimi.backend.repository.RecruitmentGroupRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogService;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private DirectionRepository directionRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private RecruitmentGroupRepository recruitmentGroupRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private PeriodService periodService;
    @Mock
    private AuditLogService auditLogService;

    private RecruitmentApplicationProperties recruitmentApplicationProperties;
    private ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        recruitmentApplicationProperties = new RecruitmentApplicationProperties();
        recruitmentApplicationProperties.setAllowedGrades(EnumSet.of(Grade.YEAR_1, Grade.YEAR_2));
        Clock clock = Clock.fixed(Instant.parse("2026-06-28T02:00:00Z"), APP_ZONE);
        applicationService = new ApplicationService(
                applicationRepository,
                directionRepository,
                groupMemberRepository,
                recruitmentGroupRepository,
                currentUserService,
                periodService,
                recruitmentApplicationProperties,
                auditLogService,
                clock
        );
    }

    @Test
    void createApplicationShouldPersistAndReturnDirectionNames() {
        LoginUser loginUser = buildLoginUser(1L, Role.FRESHMAN);
        UpsertApplicationRequest request = new UpsertApplicationRequest(
                "张三",
                "13800000000",
                "计算机学院",
                "软件工程",
                "软工一班",
                Grade.YEAR_1,
                2026,
                10L,
                11L,
                "对后端感兴趣"
        );
        Direction level1 = Direction.builder().id(10L).name("后端").level(1).enabled(true).build();
        Direction level2 = Direction.builder().id(11L).parentId(10L).name("Java").level(2).enabled(true).build();
        Application saved = Application.builder()
                .id(100L)
                .userId(1L)
                .realName(request.realName())
                .phoneNumber(request.phone())
                .college(request.college())
                .major(request.major())
                .className(request.className())
                .grade(request.grade())
                .admissionYear(request.admissionYear())
                .directionLevel1Id(10L)
                .directionLevel2Id(11L)
                .introduction(request.introduction())
                .status(ApplicationStatus.SUBMITTED)
                .createdAt(LocalDateTime.of(2026, 6, 28, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 6, 28, 10, 0))
                .build();

        when(currentUserService.requireCurrentUser()).thenReturn(loginUser);
        doNothing().when(periodService).ensureRegistrationOpen();
        when(directionRepository.findById(10L)).thenReturn(Optional.of(level1));
        when(directionRepository.findById(11L)).thenReturn(Optional.of(level2));
        when(applicationRepository.existsByUserIdAndDirectionLevel2Id(1L, 11L)).thenReturn(false);
        when(applicationRepository.save(any(Application.class))).thenReturn(saved);
        when(directionRepository.findAllById(any(Iterable.class))).thenReturn(List.of(level1, level2));
        when(groupMemberRepository.findAllByApplicationIdIn(List.of(100L))).thenReturn(List.of());

        var result = applicationService.createApplication(request);

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.directionLevel1Name()).isEqualTo("后端");
        assertThat(result.directionLevel2Name()).isEqualTo("Java");
        assertThat(result.groupId()).isNull();

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
    }

    @Test
    void createApplicationShouldRejectDisallowedGradeFromConfig() {
        LoginUser loginUser = buildLoginUser(1L, Role.FRESHMAN);
        UpsertApplicationRequest request = new UpsertApplicationRequest(
                "张三",
                "13800000000",
                "计算机学院",
                "软件工程",
                "软工一班",
                Grade.YEAR_3,
                2024,
                10L,
                11L,
                null
        );
        when(currentUserService.requireCurrentUser()).thenReturn(loginUser);
        doNothing().when(periodService).ensureRegistrationOpen();

        assertThatThrownBy(() -> applicationService.createApplication(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("当前年级暂不允许报名");
    }

    @Test
    void updateApplicationShouldRejectWhenAlreadyGrouped() {
        LoginUser loginUser = buildLoginUser(1L, Role.FRESHMAN);
        Application application = Application.builder()
                .id(100L)
                .userId(1L)
                .status(ApplicationStatus.SUBMITTED)
                .build();
        UpsertApplicationRequest request = new UpsertApplicationRequest(
                "张三",
                "13800000000",
                "计算机学院",
                "软件工程",
                "软工一班",
                Grade.YEAR_1,
                2026,
                10L,
                11L,
                null
        );

        when(currentUserService.requireCurrentUser()).thenReturn(loginUser);
        doNothing().when(periodService).ensureRegistrationOpen();
        when(applicationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(application));
        when(groupMemberRepository.findByApplicationId(100L)).thenReturn(Optional.of(GroupMember.builder()
                .id(1L)
                .groupId(20L)
                .userId(1L)
                .applicationId(100L)
                .build()));

        assertThatThrownBy(() -> applicationService.updateApplication(100L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("已分组的申请不允许修改");
    }

    @Test
    void withdrawApplicationShouldMarkStatusWithdrawn() {
        LoginUser loginUser = buildLoginUser(1L, Role.FRESHMAN);
        Application application = Application.builder()
                .id(100L)
                .userId(1L)
                .status(ApplicationStatus.SUBMITTED)
                .build();

        when(currentUserService.requireCurrentUser()).thenReturn(loginUser);
        doNothing().when(periodService).ensureRegistrationOpen();
        when(applicationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(application));
        when(groupMemberRepository.findByApplicationId(100L)).thenReturn(Optional.empty());

        applicationService.withdrawApplication(100L);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        verify(applicationRepository).save(application);
    }

    private LoginUser buildLoginUser(Long userId, Role role) {
        return new LoginUser(
                userId,
                "tester",
                "tester@example.com",
                "encoded",
                role,
                UserStatus.ACTIVE,
                0L,
                "jti"
        );
    }
}
