package club.muimi.backend.service.application;

import club.muimi.backend.common.enums.ApplicationStatus;
import club.muimi.backend.common.enums.Grade;
import club.muimi.backend.common.enums.Role;
import club.muimi.backend.config.RecruitmentApplicationProperties;
import club.muimi.backend.dto.application.UpsertApplicationRequest;
import club.muimi.backend.entity.Application;
import club.muimi.backend.entity.Direction;
import club.muimi.backend.entity.GroupMember;
import club.muimi.backend.entity.RecruitmentGroup;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.exception.NotFoundException;
import club.muimi.backend.exception.ValidationException;
import club.muimi.backend.repository.ApplicationRepository;
import club.muimi.backend.repository.DirectionRepository;
import club.muimi.backend.repository.GroupMemberRepository;
import club.muimi.backend.repository.RecruitmentGroupRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogCommand;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.period.PeriodService;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.vo.application.ApplicationDetailVo;
import club.muimi.backend.vo.application.ApplicationSummaryVo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final DirectionRepository directionRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final RecruitmentGroupRepository recruitmentGroupRepository;
    private final CurrentUserService currentUserService;
    private final PeriodService periodService;
    private final RecruitmentApplicationProperties recruitmentApplicationProperties;
    private final AuditLogService auditLogService;
    private final Clock appClock;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            DirectionRepository directionRepository,
            GroupMemberRepository groupMemberRepository,
            RecruitmentGroupRepository recruitmentGroupRepository,
            CurrentUserService currentUserService,
            PeriodService periodService,
            RecruitmentApplicationProperties recruitmentApplicationProperties,
            AuditLogService auditLogService,
            Clock appClock
    ) {
        this.applicationRepository = applicationRepository;
        this.directionRepository = directionRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.recruitmentGroupRepository = recruitmentGroupRepository;
        this.currentUserService = currentUserService;
        this.periodService = periodService;
        this.recruitmentApplicationProperties = recruitmentApplicationProperties;
        this.auditLogService = auditLogService;
        this.appClock = appClock;
    }

    @Transactional(readOnly = true)
    public List<ApplicationDetailVo> listCurrentUserApplications() {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        List<Application> applications = applicationRepository.findAllByUserIdOrderByCreatedAtDesc(currentUser.getUserId());
        return buildDetailVos(applications);
    }

    @Transactional(readOnly = true)
    public ApplicationDetailVo getCurrentUserApplication(Long applicationId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        Application application = getOwnedApplicationOrThrow(applicationId, currentUser.getUserId());
        return buildDetailVos(List.of(application)).getFirst();
    }

    @Transactional(readOnly = true)
    public ApplicationSummaryVo getCurrentUserSummary() {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        List<Application> applications = applicationRepository.findAllByUserIdOrderByCreatedAtDesc(currentUser.getUserId());
        if (applications.isEmpty()) {
            return new ApplicationSummaryVo(0, 0, 0, List.of());
        }

        List<Long> applicationIds = applications.stream().map(Application::getId).toList();
        Map<Long, GroupMember> groupMemberMap = groupMemberRepository.findAllByApplicationIdIn(applicationIds).stream()
                .collect(Collectors.toMap(GroupMember::getApplicationId, Function.identity()));

        long groupedCount = groupMemberMap.size();
        long submittedCount = applications.stream()
                .filter(application -> application.getStatus() == ApplicationStatus.SUBMITTED)
                .filter(application -> !groupMemberMap.containsKey(application.getId()))
                .count();
        List<Long> groupIds = groupMemberMap.values().stream()
                .map(GroupMember::getGroupId)
                .distinct()
                .sorted()
                .toList();

        return new ApplicationSummaryVo(
                applications.size(),
                submittedCount,
                groupedCount,
                groupIds
        );
    }

    @Transactional
    public ApplicationDetailVo createApplication(UpsertApplicationRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        ensureFreshmanWritable(currentUser);
        periodService.ensureRegistrationOpen();

        DirectionSelection directionSelection = validateRequestPayload(request);
        if (applicationRepository.existsByUserIdAndDirectionLevel2Id(currentUser.getUserId(), request.directionLevel2Id())) {
            throw new ConflictException("同一方向只能提交一份报名申请");
        }

        Application application = Application.builder()
                .userId(currentUser.getUserId())
                .realName(request.realName().trim())
                .phoneNumber(request.phone().trim())
                .college(request.college().trim())
                .major(request.major().trim())
                .className(request.className().trim())
                .grade(request.grade())
                .admissionYear(request.admissionYear())
                .directionLevel1Id(directionSelection.level1().getId())
                .directionLevel2Id(directionSelection.level2().getId())
                .introduction(normalizeNullableText(request.introduction()))
                .status(ApplicationStatus.SUBMITTED)
                .statusRemark(null)
                .build();

        Application saved = saveApplicationHandlingDuplicate(application);
        recordApplicationAudit("CREATE_APPLICATION", "提交报名申请", currentUser, saved, directionSelection);
        return buildDetailVos(List.of(saved)).getFirst();
    }

    @Transactional
    public ApplicationDetailVo updateApplication(Long applicationId, UpsertApplicationRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        ensureFreshmanWritable(currentUser);
        periodService.ensureRegistrationOpen();

        Application application = getOwnedApplicationForUpdateOrThrow(applicationId, currentUser.getUserId());
        ensureApplicationEditable(application, "修改");
        DirectionSelection directionSelection = validateRequestPayload(request);

        if (applicationRepository.existsByUserIdAndDirectionLevel2IdAndIdNot(
                currentUser.getUserId(),
                request.directionLevel2Id(),
                applicationId
        )) {
            throw new ConflictException("同一方向只能提交一份报名申请");
        }

        application.setRealName(request.realName().trim());
        application.setPhoneNumber(request.phone().trim());
        application.setCollege(request.college().trim());
        application.setMajor(request.major().trim());
        application.setClassName(request.className().trim());
        application.setGrade(request.grade());
        application.setAdmissionYear(request.admissionYear());
        application.setDirectionLevel1Id(directionSelection.level1().getId());
        application.setDirectionLevel2Id(directionSelection.level2().getId());
        application.setIntroduction(normalizeNullableText(request.introduction()));

        Application saved = saveApplicationHandlingDuplicate(application);
        recordApplicationAudit("UPDATE_APPLICATION", "修改报名申请", currentUser, saved, directionSelection);
        return buildDetailVos(List.of(saved)).getFirst();
    }

    @Transactional
    public void withdrawApplication(Long applicationId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        ensureFreshmanWritable(currentUser);
        periodService.ensureRegistrationOpen();

        Application application = getOwnedApplicationForUpdateOrThrow(applicationId, currentUser.getUserId());
        ensureApplicationEditable(application, "撤回");
        application.setStatus(ApplicationStatus.WITHDRAWN);
        applicationRepository.save(application);
        recordApplicationAudit("WITHDRAW_APPLICATION", "撤回报名申请", currentUser, application, null);
    }

    private Application getOwnedApplicationOrThrow(Long applicationId, Long userId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("报名申请不存在"));
        if (!application.getUserId().equals(userId)) {
            throw new ForbiddenException("无权访问该报名申请");
        }
        return application;
    }

    private Application getOwnedApplicationForUpdateOrThrow(Long applicationId, Long userId) {
        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new NotFoundException("报名申请不存在"));
        if (!application.getUserId().equals(userId)) {
            throw new ForbiddenException("无权访问该报名申请");
        }
        return application;
    }

    private void ensureFreshmanWritable(LoginUser currentUser) {
        if (currentUser.getRole() != Role.FRESHMAN) {
            throw new ForbiddenException("当前角色不允许进行报名操作");
        }
    }

    private DirectionSelection validateRequestPayload(UpsertApplicationRequest request) {
        validateGradeAllowed(request.grade());
        validateAdmissionYear(request.admissionYear());

        Direction level1 = directionRepository.findById(request.directionLevel1Id())
                .orElseThrow(() -> new ValidationException("所选一级方向不存在或不可用"));
        Direction level2 = directionRepository.findById(request.directionLevel2Id())
                .orElseThrow(() -> new ValidationException("所选二级方向不存在或不可用"));

        if (!Boolean.TRUE.equals(level1.getEnabled()) || level1.getLevel() != 1) {
            throw new ValidationException("所选一级方向不存在或不可用");
        }
        if (!Boolean.TRUE.equals(level2.getEnabled()) || level2.getLevel() != 2) {
            throw new ValidationException("所选二级方向不存在或不可用");
        }
        if (!Objects.equals(level2.getParentId(), level1.getId())) {
            throw new ValidationException("二级方向不属于所选一级方向");
        }
        return new DirectionSelection(level1, level2);
    }

    private void validateGradeAllowed(Grade grade) {
        if (!recruitmentApplicationProperties.getAllowedGrades().contains(grade)) {
            throw new ValidationException("当前年级暂不允许报名");
        }
    }

    private void validateAdmissionYear(Integer admissionYear) {
        int currentYear = LocalDate.now(appClock).getYear();
        if (admissionYear < currentYear - 6 || admissionYear > currentYear + 1) {
            throw new ValidationException("入学年份不合法");
        }
    }

    private void ensureApplicationEditable(Application application, String action) {
        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new ConflictException("当前申请状态不允许" + action);
        }
        if (groupMemberRepository.findByApplicationId(application.getId()).isPresent()) {
            throw new ConflictException("已分组的申请不允许" + action);
        }
    }

    private Application saveApplicationHandlingDuplicate(Application application) {
        try {
            return applicationRepository.save(application);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("同一方向只能提交一份报名申请");
        }
    }

    private List<ApplicationDetailVo> buildDetailVos(List<Application> applications) {
        if (applications.isEmpty()) {
            return List.of();
        }

        List<Long> applicationIds = applications.stream().map(Application::getId).toList();
        Set<Long> directionIds = new LinkedHashSet<>();
        for (Application application : applications) {
            directionIds.add(application.getDirectionLevel1Id());
            directionIds.add(application.getDirectionLevel2Id());
        }

        Map<Long, Direction> directionMap = directionRepository.findAllById(directionIds).stream()
                .collect(Collectors.toMap(Direction::getId, Function.identity()));
        Map<Long, GroupMember> groupMemberMap = groupMemberRepository.findAllByApplicationIdIn(applicationIds).stream()
                .collect(Collectors.toMap(GroupMember::getApplicationId, Function.identity()));
        Set<Long> groupIds = groupMemberMap.values().stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, RecruitmentGroup> groupMap = groupIds.isEmpty()
                ? Map.of()
                : recruitmentGroupRepository.findAllByIdIn(groupIds).stream()
                .collect(Collectors.toMap(RecruitmentGroup::getId, Function.identity()));

        return applications.stream()
                .map(application -> toDetailVo(application, directionMap, groupMemberMap, groupMap))
                .toList();
    }

    private ApplicationDetailVo toDetailVo(
            Application application,
            Map<Long, Direction> directionMap,
            Map<Long, GroupMember> groupMemberMap,
            Map<Long, RecruitmentGroup> groupMap
    ) {
        Direction level1 = directionMap.get(application.getDirectionLevel1Id());
        Direction level2 = directionMap.get(application.getDirectionLevel2Id());
        GroupMember groupMember = groupMemberMap.get(application.getId());
        RecruitmentGroup group = groupMember == null ? null : groupMap.get(groupMember.getGroupId());

        return new ApplicationDetailVo(
                application.getId(),
                application.getRealName(),
                application.getPhoneNumber(),
                application.getCollege(),
                application.getMajor(),
                application.getClassName(),
                application.getGrade(),
                application.getAdmissionYear(),
                application.getDirectionLevel1Id(),
                level1 == null ? null : level1.getName(),
                application.getDirectionLevel2Id(),
                level2 == null ? null : level2.getName(),
                application.getIntroduction(),
                application.getStatus(),
                application.getStatusRemark(),
                group == null ? null : group.getId(),
                group == null ? null : group.getName(),
                toOffsetDateTime(application.getCreatedAt()),
                toOffsetDateTime(application.getUpdatedAt())
        );
    }

    private OffsetDateTime toOffsetDateTime(java.time.LocalDateTime value) {
        return value.atZone(appClock.getZone()).toOffsetDateTime();
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void recordApplicationAudit(
            String action,
            String summary,
            LoginUser actor,
            Application application,
            DirectionSelection directionSelection
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("userId", application.getUserId());
        detail.put("status", application.getStatus());
        detail.put("grade", application.getGrade());
        detail.put("admissionYear", application.getAdmissionYear());
        detail.put("directionLevel1Id", application.getDirectionLevel1Id());
        detail.put("directionLevel2Id", application.getDirectionLevel2Id());
        if (directionSelection != null) {
            detail.put("directionLevel1Name", directionSelection.level1().getName());
            detail.put("directionLevel2Name", directionSelection.level2().getName());
        }
        auditLogService.record(AuditLogCommand.builder(
                        club.muimi.backend.common.enums.AuditModule.APPLICATION,
                        action,
                        club.muimi.backend.common.enums.AuditSeverity.IMPORTANT,
                        summary
                ).actor(actor)
                .target("APPLICATION", application.getId())
                .detail(detail)
                .build());
    }

    private record DirectionSelection(Direction level1, Direction level2) {
    }
}
