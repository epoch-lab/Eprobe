package club.muimi.backend.service.announcement;

import club.muimi.backend.common.enums.AnnouncementScope;
import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.dto.announcement.UpsertAnnouncementRequest;
import club.muimi.backend.entity.Announcement;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.repository.AnnouncementRepository;
import club.muimi.backend.repository.GroupMemberRepository;
import club.muimi.backend.repository.RecruitmentGroupRepository;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.notification.NotificationService;
import club.muimi.backend.service.user.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock
    private AnnouncementRepository announcementRepository;
    @Mock
    private RecruitmentGroupRepository recruitmentGroupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogService auditLogService;

    private AnnouncementService announcementService;

    @BeforeEach
    void setUp() {
        announcementService = new AnnouncementService(
                announcementRepository,
                recruitmentGroupRepository,
                groupMemberRepository,
                userRepository,
                currentUserService,
                notificationService,
                auditLogService,
                Clock.fixed(Instant.parse("2026-06-29T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void updateAnnouncementShouldRejectVisibilityChange() {
        Announcement announcement = Announcement.builder()
                .id(1L)
                .title("旧公告")
                .contentMarkdown("内容")
                .scope(AnnouncementScope.GROUP)
                .groupId(10L)
                .publisherUserId(2L)
                .build();
        when(currentUserService.requireCurrentUser()).thenReturn(new LoginUser(
                1L,
                "admin",
                "admin@example.com",
                "hashed",
                Role.ADMIN,
                UserStatus.ACTIVE,
                0L,
                "jti-admin"
        ));
        when(announcementRepository.findById(1L)).thenReturn(Optional.of(announcement));
        when(recruitmentGroupRepository.findById(20L)).thenReturn(Optional.of(
                club.muimi.backend.entity.RecruitmentGroup.builder()
                        .id(20L)
                        .name("g2")
                        .directionLevel1Id(1L)
                        .directionLevel2Id(2L)
                        .grade(club.muimi.backend.common.enums.Grade.YEAR_1)
                        .admissionYear(2026)
                        .maxSize(10)
                        .build()
        ));

        assertThatThrownBy(() -> announcementService.updateAnnouncement(
                1L,
                new UpsertAnnouncementRequest("新公告", "新内容", AnnouncementScope.GROUP, 20L)
        )).isInstanceOf(ConflictException.class)
                .hasMessage("公告发布后不允许修改可见范围");
    }
}
