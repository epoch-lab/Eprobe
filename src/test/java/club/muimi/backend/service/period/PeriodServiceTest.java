package club.muimi.backend.service.period;

import club.muimi.backend.common.enums.PeriodType;
import club.muimi.backend.dto.admin.PeriodConfigRequest;
import club.muimi.backend.entity.RecruitmentPeriod;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.PeriodNotAllowedException;
import club.muimi.backend.repository.RecruitmentPeriodRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.user.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeriodServiceTest {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private RecruitmentPeriodRepository recruitmentPeriodRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditLogService auditLogService;

    @Test
    void getCurrentPeriodShouldReturnSelectionWhenSelectionIsOpen() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-27T02:00:00Z"), APP_ZONE);
        LocalDateTime now = LocalDateTime.now(clock);
        when(recruitmentPeriodRepository.findByPeriodTypeAndEnabledTrue(PeriodType.REGISTRATION)).thenReturn(Optional.empty());
        when(recruitmentPeriodRepository.findByPeriodTypeAndEnabledTrue(PeriodType.SELECTION)).thenReturn(Optional.of(RecruitmentPeriod.builder()
                .periodType(PeriodType.SELECTION)
                .startTime(now.minusHours(1))
                .endTime(now.plusHours(1))
                .enabled(true)
                .build()));

        PeriodService periodService = new PeriodService(recruitmentPeriodRepository, currentUserService, auditLogService, clock);

        assertThat(periodService.getCurrentPeriod()).isEqualTo(PeriodType.SELECTION);
    }

    @Test
    void ensureSelectionOpenForGroupingShouldRejectWhenSelectionClosed() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-27T02:00:00Z"), APP_ZONE);
        LocalDateTime now = LocalDateTime.now(clock);
        when(recruitmentPeriodRepository.findByPeriodTypeAndEnabledTrue(PeriodType.SELECTION)).thenReturn(Optional.of(RecruitmentPeriod.builder()
                .periodType(PeriodType.SELECTION)
                .startTime(now.minusDays(2))
                .endTime(now.minusDays(1))
                .enabled(true)
                .build()));

        PeriodService periodService = new PeriodService(recruitmentPeriodRepository, currentUserService, auditLogService, clock);

        assertThatThrownBy(periodService::ensureSelectionOpenForGrouping)
                .isInstanceOf(PeriodNotAllowedException.class)
                .hasMessage("当前不是选拔期，暂不允许分组管理");
    }

    @Test
    void getCurrentPeriodShouldReturnNotOpenBeforeAnyPeriodStarts() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), APP_ZONE);
        LocalDateTime now = LocalDateTime.now(clock);
        when(recruitmentPeriodRepository.findByPeriodTypeAndEnabledTrue(PeriodType.REGISTRATION)).thenReturn(Optional.of(RecruitmentPeriod.builder()
                .periodType(PeriodType.REGISTRATION)
                .startTime(now.plusDays(1))
                .endTime(now.plusDays(10))
                .enabled(true)
                .build()));
        when(recruitmentPeriodRepository.findByPeriodTypeAndEnabledTrue(PeriodType.SELECTION)).thenReturn(Optional.empty());
        when(recruitmentPeriodRepository.findByPeriodTypeAndEnabledTrue(PeriodType.INTERVIEW)).thenReturn(Optional.empty());
        when(recruitmentPeriodRepository.findAllByEnabledTrue()).thenReturn(List.of(RecruitmentPeriod.builder()
                .periodType(PeriodType.REGISTRATION)
                .startTime(now.plusDays(1))
                .endTime(now.plusDays(10))
                .enabled(true)
                .build()));

        PeriodService periodService = new PeriodService(recruitmentPeriodRepository, currentUserService, auditLogService, clock);

        assertThat(periodService.getCurrentPeriod()).isEqualTo(PeriodType.NOT_OPEN);
    }

    @Test
    void getCurrentPeriodShouldReturnNotOpenDuringGapBetweenPeriods() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-10T00:00:00Z"), APP_ZONE);
        LocalDateTime now = LocalDateTime.now(clock);
        when(recruitmentPeriodRepository.findByPeriodTypeAndEnabledTrue(PeriodType.REGISTRATION)).thenReturn(Optional.of(RecruitmentPeriod.builder()
                .periodType(PeriodType.REGISTRATION)
                .startTime(now.minusDays(10))
                .endTime(now.minusDays(5))
                .enabled(true)
                .build()));
        when(recruitmentPeriodRepository.findByPeriodTypeAndEnabledTrue(PeriodType.SELECTION)).thenReturn(Optional.of(RecruitmentPeriod.builder()
                .periodType(PeriodType.SELECTION)
                .startTime(now.plusDays(2))
                .endTime(now.plusDays(6))
                .enabled(true)
                .build()));
        when(recruitmentPeriodRepository.findByPeriodTypeAndEnabledTrue(PeriodType.INTERVIEW)).thenReturn(Optional.empty());
        when(recruitmentPeriodRepository.findAllByEnabledTrue()).thenReturn(List.of(
                RecruitmentPeriod.builder()
                        .periodType(PeriodType.REGISTRATION)
                        .startTime(now.minusDays(10))
                        .endTime(now.minusDays(5))
                        .enabled(true)
                        .build(),
                RecruitmentPeriod.builder()
                        .periodType(PeriodType.SELECTION)
                        .startTime(now.plusDays(2))
                        .endTime(now.plusDays(6))
                        .enabled(true)
                        .build()
        ));

        PeriodService periodService = new PeriodService(recruitmentPeriodRepository, currentUserService, auditLogService, clock);

        assertThat(periodService.getCurrentPeriod()).isEqualTo(PeriodType.NOT_OPEN);
    }

    @Test
    void getCurrentPeriodShouldReturnFinishedAfterLatestPeriodEnds() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), APP_ZONE);
        LocalDateTime now = LocalDateTime.now(clock);
        when(recruitmentPeriodRepository.findByPeriodTypeAndEnabledTrue(PeriodType.REGISTRATION)).thenReturn(Optional.empty());
        when(recruitmentPeriodRepository.findByPeriodTypeAndEnabledTrue(PeriodType.SELECTION)).thenReturn(Optional.empty());
        when(recruitmentPeriodRepository.findByPeriodTypeAndEnabledTrue(PeriodType.INTERVIEW)).thenReturn(Optional.of(RecruitmentPeriod.builder()
                .periodType(PeriodType.INTERVIEW)
                .startTime(now.minusDays(10))
                .endTime(now.minusDays(1))
                .enabled(true)
                .build()));
        when(recruitmentPeriodRepository.findAllByEnabledTrue()).thenReturn(List.of(RecruitmentPeriod.builder()
                .periodType(PeriodType.INTERVIEW)
                .startTime(now.minusDays(10))
                .endTime(now.minusDays(1))
                .enabled(true)
                .build()));

        PeriodService periodService = new PeriodService(recruitmentPeriodRepository, currentUserService, auditLogService, clock);

        assertThat(periodService.getCurrentPeriod()).isEqualTo(PeriodType.FINISHED);
    }

    @Test
    void listPeriodsShouldUseBusinessOrder() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-27T02:00:00Z"), APP_ZONE);
        when(recruitmentPeriodRepository.findAll()).thenReturn(List.of(
                RecruitmentPeriod.builder()
                        .id(2L)
                        .periodType(PeriodType.SELECTION)
                        .startTime(LocalDateTime.of(2026, 7, 1, 0, 0))
                        .endTime(LocalDateTime.of(2026, 7, 10, 0, 0))
                        .enabled(true)
                        .build(),
                RecruitmentPeriod.builder()
                        .id(1L)
                        .periodType(PeriodType.REGISTRATION)
                        .startTime(LocalDateTime.of(2026, 6, 20, 0, 0))
                        .endTime(LocalDateTime.of(2026, 6, 30, 0, 0))
                        .enabled(true)
                        .build()
        ));

        PeriodService periodService = new PeriodService(recruitmentPeriodRepository, currentUserService, auditLogService, clock);

        var result = periodService.listPeriods();

        assertThat(result).extracting(item -> item.periodType())
                .containsExactly(PeriodType.REGISTRATION, PeriodType.SELECTION);
    }

    @Test
    void savePeriodsShouldRejectOverlappingPeriods() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-27T02:00:00Z"), APP_ZONE);
        when(recruitmentPeriodRepository.findAll()).thenReturn(List.of());

        PeriodService periodService = new PeriodService(recruitmentPeriodRepository, currentUserService, auditLogService, clock);
        when(currentUserService.requireCurrentUser()).thenReturn(buildAdminLoginUser());

        assertThatThrownBy(() -> periodService.savePeriods(List.of(
                new PeriodConfigRequest(
                        PeriodType.REGISTRATION,
                        OffsetDateTime.parse("2026-06-20T00:00:00+08:00"),
                        OffsetDateTime.parse("2026-06-30T00:00:00+08:00"),
                        true
                ),
                new PeriodConfigRequest(
                        PeriodType.SELECTION,
                        OffsetDateTime.parse("2026-06-29T00:00:00+08:00"),
                        OffsetDateTime.parse("2026-07-10T00:00:00+08:00"),
                        true
                )
        ))).isInstanceOf(ConflictException.class)
                .hasMessage("时期时间不能重叠");
    }

    @Test
    void savePeriodsShouldRejectOverlappingDisabledPeriods() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-27T02:00:00Z"), APP_ZONE);
        when(recruitmentPeriodRepository.findAll()).thenReturn(List.of());

        PeriodService periodService = new PeriodService(recruitmentPeriodRepository, currentUserService, auditLogService, clock);
        when(currentUserService.requireCurrentUser()).thenReturn(buildAdminLoginUser());

        assertThatThrownBy(() -> periodService.savePeriods(List.of(
                new PeriodConfigRequest(
                        PeriodType.REGISTRATION,
                        OffsetDateTime.parse("2026-06-20T00:00:00+08:00"),
                        OffsetDateTime.parse("2026-06-30T00:00:00+08:00"),
                        true
                ),
                new PeriodConfigRequest(
                        PeriodType.SELECTION,
                        OffsetDateTime.parse("2026-06-29T00:00:00+08:00"),
                        OffsetDateTime.parse("2026-07-10T00:00:00+08:00"),
                        false
                )
        ))).isInstanceOf(ConflictException.class)
                .hasMessage("时期时间不能重叠");
    }

    @Test
    void updatePeriodShouldRejectChangingPeriodType() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-27T02:00:00Z"), APP_ZONE);
        when(recruitmentPeriodRepository.findById(1L)).thenReturn(Optional.of(RecruitmentPeriod.builder()
                .id(1L)
                .periodType(PeriodType.REGISTRATION)
                .startTime(LocalDateTime.of(2026, 6, 20, 0, 0))
                .endTime(LocalDateTime.of(2026, 6, 30, 0, 0))
                .enabled(true)
                .build()));

        PeriodService periodService = new PeriodService(recruitmentPeriodRepository, currentUserService, auditLogService, clock);
        when(currentUserService.requireCurrentUser()).thenReturn(buildAdminLoginUser());

        assertThatThrownBy(() -> periodService.updatePeriod(1L, new PeriodConfigRequest(
                PeriodType.SELECTION,
                OffsetDateTime.parse("2026-07-01T00:00:00+08:00"),
                OffsetDateTime.parse("2026-07-10T00:00:00+08:00"),
                true
        ))).isInstanceOf(ConflictException.class)
                .hasMessage("不允许修改时期类型");
    }

    private LoginUser buildAdminLoginUser() {
        return new LoginUser(
                1L,
                "admin",
                "admin@example.com",
                "hashed",
                club.muimi.backend.common.enums.Role.ADMIN,
                club.muimi.backend.common.enums.UserStatus.ACTIVE,
                0L,
                "jti-admin"
        );
    }
}
