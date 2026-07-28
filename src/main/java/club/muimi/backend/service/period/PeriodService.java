package club.muimi.backend.service.period;

import club.muimi.backend.common.enums.AuditModule;
import club.muimi.backend.common.enums.AuditSeverity;
import club.muimi.backend.common.enums.PeriodType;
import club.muimi.backend.dto.admin.PeriodConfigRequest;
import club.muimi.backend.entity.RecruitmentPeriod;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.NotFoundException;
import club.muimi.backend.exception.PeriodNotAllowedException;
import club.muimi.backend.repository.RecruitmentPeriodRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogCommand;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.vo.admin.AdminPeriodVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class PeriodService {

    private static final Map<PeriodType, Integer> PERIOD_ORDER = new EnumMap<>(PeriodType.class);

    static {
        PERIOD_ORDER.put(PeriodType.REGISTRATION, 1);
        PERIOD_ORDER.put(PeriodType.SELECTION, 2);
        PERIOD_ORDER.put(PeriodType.INTERVIEW, 3);
        PERIOD_ORDER.put(PeriodType.NOT_OPEN, 99);
        PERIOD_ORDER.put(PeriodType.FINISHED, 100);
    }

    private final RecruitmentPeriodRepository recruitmentPeriodRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final Clock appClock;

    public PeriodService(
            RecruitmentPeriodRepository recruitmentPeriodRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            Clock appClock
    ) {
        this.recruitmentPeriodRepository = recruitmentPeriodRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.appClock = appClock;
    }

    @Transactional(readOnly = true)
    public List<AdminPeriodVo> listPeriods() {
        return recruitmentPeriodRepository.findAll().stream()
                .sorted(periodComparator())
                .map(this::toAdminPeriodVo)
                .toList();
    }

    @Transactional
    public List<AdminPeriodVo> savePeriods(List<PeriodConfigRequest> requests) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        validatePeriodTypesUnique(requests);
        Map<PeriodType, RecruitmentPeriod> periodMap = recruitmentPeriodRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(RecruitmentPeriod::getPeriodType, period -> period));

        for (PeriodConfigRequest request : requests) {
            validatePeriodTypeEditable(request.periodType());
            RecruitmentPeriod period = periodMap.getOrDefault(request.periodType(), RecruitmentPeriod.builder()
                    .periodType(request.periodType())
                    .build());
            applyPeriodConfig(period, request);
            periodMap.put(request.periodType(), period);
        }

        validatePeriods(periodMap.values().stream().toList());
        recruitmentPeriodRepository.saveAll(periodMap.values());
        recordPeriodBatchAudit(currentUser, requests);
        return periodMap.values().stream()
                .sorted(periodComparator())
                .map(this::toAdminPeriodVo)
                .toList();
    }

    @Transactional
    public AdminPeriodVo updatePeriod(Long periodId, PeriodConfigRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        validatePeriodTypeEditable(request.periodType());
        RecruitmentPeriod period = recruitmentPeriodRepository.findById(periodId)
                .orElseThrow(() -> new NotFoundException("时期配置不存在"));
        if (period.getPeriodType() != request.periodType()) {
            throw new ConflictException("不允许修改时期类型");
        }

        applyPeriodConfig(period, request);
        List<RecruitmentPeriod> mergedPeriods = recruitmentPeriodRepository.findAll().stream()
                .map(existing -> existing.getId().equals(periodId) ? period : existing)
                .toList();
        validatePeriods(mergedPeriods);
        RecruitmentPeriod saved = recruitmentPeriodRepository.save(period);
        recordPeriodAudit("UPDATE_PERIOD", "更新时期配置", currentUser, saved);
        return toAdminPeriodVo(saved);
    }

    public boolean isRegistrationOpen() {
        return isPeriodOpen(PeriodType.REGISTRATION, now());
    }

    public boolean isSelectionOpen() {
        return isPeriodOpen(PeriodType.SELECTION, now());
    }

    public boolean isInterviewOpen() {
        return isPeriodOpen(PeriodType.INTERVIEW, now());
    }

    public PeriodType getCurrentPeriod() {
        LocalDateTime currentTime = now();
        if (isPeriodOpen(PeriodType.REGISTRATION, currentTime)) {
            return PeriodType.REGISTRATION;
        }
        if (isPeriodOpen(PeriodType.SELECTION, currentTime)) {
            return PeriodType.SELECTION;
        }
        if (isPeriodOpen(PeriodType.INTERVIEW, currentTime)) {
            return PeriodType.INTERVIEW;
        }

        List<RecruitmentPeriod> enabledPeriods = recruitmentPeriodRepository.findAllByEnabledTrue();
        if (enabledPeriods.isEmpty()) {
            return PeriodType.NOT_OPEN;
        }

        LocalDateTime latestEndTime = enabledPeriods.stream()
                .map(RecruitmentPeriod::getEndTime)
                .max(LocalDateTime::compareTo)
                .orElse(currentTime);
        if (currentTime.isAfter(latestEndTime)) {
            return PeriodType.FINISHED;
        }
        return PeriodType.NOT_OPEN;
    }

    public void ensureRegistrationOpen() {
        if (!isRegistrationOpen()) {
            throw new PeriodNotAllowedException("当前不是报名期，暂不允许该操作");
        }
    }

    public void ensureSelectionOpenForGrouping() {
        if (!isSelectionOpen()) {
            throw new PeriodNotAllowedException("当前不是选拔期，暂不允许分组管理");
        }
    }

    public void ensureSelectionOpenForTaskSubmit() {
        if (!isSelectionOpen()) {
            throw new PeriodNotAllowedException("当前不是选拔期，暂不允许提交任务");
        }
    }

    public void ensureSelectionOpenForTaskManage() {
        if (!isSelectionOpen()) {
            throw new PeriodNotAllowedException("当前不是选拔期，暂不允许任务管理");
        }
    }

    private boolean isPeriodOpen(PeriodType periodType, LocalDateTime now) {
        return recruitmentPeriodRepository.findByPeriodTypeAndEnabledTrue(periodType)
                .filter(period -> !now.isBefore(period.getStartTime()) && !now.isAfter(period.getEndTime()))
                .map(RecruitmentPeriod::getEnabled)
                .orElse(false);
    }

    private void applyPeriodConfig(RecruitmentPeriod period, PeriodConfigRequest request) {
        period.setPeriodType(request.periodType());
        period.setStartTime(request.startTime().atZoneSameInstant(appClock.getZone()).toLocalDateTime());
        period.setEndTime(request.endTime().atZoneSameInstant(appClock.getZone()).toLocalDateTime());
        period.setEnabled(request.enabled());
    }

    private void validatePeriods(List<RecruitmentPeriod> periods) {
        for (RecruitmentPeriod period : periods) {
            validatePeriodTypeEditable(period.getPeriodType());
            if (!period.getStartTime().isBefore(period.getEndTime())) {
                throw new ConflictException("时期开始时间必须早于结束时间");
            }
        }

        List<RecruitmentPeriod> sortedPeriods = periods.stream()
                .sorted(Comparator.comparing(RecruitmentPeriod::getStartTime))
                .toList();
        for (int i = 1; i < sortedPeriods.size(); i++) {
            RecruitmentPeriod previous = sortedPeriods.get(i - 1);
            RecruitmentPeriod current = sortedPeriods.get(i);
            if (!previous.getEndTime().isBefore(current.getStartTime())) {
                throw new ConflictException("时期时间不能重叠");
            }
        }
    }

    private void validatePeriodTypesUnique(List<PeriodConfigRequest> requests) {
        java.util.Set<PeriodType> types = new java.util.HashSet<>();
        for (PeriodConfigRequest request : requests) {
            validatePeriodTypeEditable(request.periodType());
            if (!types.add(request.periodType())) {
                throw new ConflictException("同一时期类型不能重复提交");
            }
        }
    }

    private void validatePeriodTypeEditable(PeriodType periodType) {
        if (periodType == PeriodType.NOT_OPEN || periodType == PeriodType.FINISHED) {
            throw new ConflictException("该时期类型不允许配置");
        }
    }

    private AdminPeriodVo toAdminPeriodVo(RecruitmentPeriod period) {
        ZoneId zoneId = appClock.getZone();
        return new AdminPeriodVo(
                period.getId(),
                period.getPeriodType(),
                period.getStartTime().atZone(zoneId).toOffsetDateTime(),
                period.getEndTime().atZone(zoneId).toOffsetDateTime(),
                period.getEnabled()
        );
    }

    private Comparator<RecruitmentPeriod> periodComparator() {
        return Comparator.comparingInt(period -> PERIOD_ORDER.getOrDefault(period.getPeriodType(), Integer.MAX_VALUE));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appClock);
    }

    private void recordPeriodBatchAudit(LoginUser actor, List<PeriodConfigRequest> requests) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("periodTypes", requests.stream().map(PeriodConfigRequest::periodType).toList());
        detail.put("enabledTypes", requests.stream().filter(PeriodConfigRequest::enabled).map(PeriodConfigRequest::periodType).toList());
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.CONFIG,
                        "SAVE_PERIODS",
                        AuditSeverity.IMPORTANT,
                        "批量保存时期配置"
                ).actor(actor)
                .target("PERIOD_CONFIG", null)
                .detail(detail)
                .build());
    }

    private void recordPeriodAudit(String action, String summary, LoginUser actor, RecruitmentPeriod period) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("periodType", period.getPeriodType());
        detail.put("startTime", period.getStartTime());
        detail.put("endTime", period.getEndTime());
        detail.put("enabled", period.getEnabled());
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.CONFIG,
                        action,
                        AuditSeverity.IMPORTANT,
                        summary
                ).actor(actor)
                .target("PERIOD", period.getId())
                .detail(detail)
                .build());
    }
}
