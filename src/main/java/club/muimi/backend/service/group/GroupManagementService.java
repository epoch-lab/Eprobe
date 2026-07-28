package club.muimi.backend.service.group;

import club.muimi.backend.common.enums.*;
import club.muimi.backend.entity.*;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.exception.NotFoundException;
import club.muimi.backend.repository.*;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogCommand;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.notification.NotificationCommand;
import club.muimi.backend.service.notification.NotificationService;
import club.muimi.backend.service.period.PeriodService;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.vo.group.GroupDetailVo;
import club.muimi.backend.vo.group.GroupMemberVo;
import club.muimi.backend.vo.group.ManageableGroupVo;
import club.muimi.backend.vo.group.UngroupedApplicationVo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GroupManagementService {

    private final RecruitmentGroupRepository recruitmentGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ApplicationRepository applicationRepository;
    private final DirectionRepository directionRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final PeriodService periodService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final Clock appClock;

    public GroupManagementService(
            RecruitmentGroupRepository recruitmentGroupRepository,
            GroupMemberRepository groupMemberRepository,
            ApplicationRepository applicationRepository,
            DirectionRepository directionRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            PeriodService periodService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            Clock appClock
    ) {
        this.recruitmentGroupRepository = recruitmentGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.applicationRepository = applicationRepository;
        this.directionRepository = directionRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.periodService = periodService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.appClock = appClock;
    }

    @Transactional(readOnly = true)
    public List<ManageableGroupVo> listManageableGroups() {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        List<RecruitmentGroup> groups = switch (currentUser.getRole()) {
            case ADMIN -> recruitmentGroupRepository.findAllByOrderByCreatedAtDesc();
            case LEADER ->
                    recruitmentGroupRepository.findAllByLeaderUserIdOrderByCreatedAtDesc(currentUser.getUserId());
            default -> throw new ForbiddenException("当前角色无权查看分组管理列表");
        };
        return buildManageableGroupVos(groups);
    }

    @Transactional(readOnly = true)
    public List<UngroupedApplicationVo> listUngroupedApplications() {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.LEADER) {
            throw new ForbiddenException("当前角色无权查看未分组报名申请");
        }

        List<Application> applications = applicationRepository.findAllUngroupedSubmittedApplications(
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return buildUngroupedApplicationVos(applications);
    }

    @Transactional
    public void assignApplicationToGroup(Long groupId, Long applicationId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        periodService.ensureSelectionOpenForGrouping();

        // Fixed lock order: lock group first, then lock application, to avoid future deadlocks.
        RecruitmentGroup group = recruitmentGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new NotFoundException("分组不存在"));
        ensureCanManageGroup(currentUser, group);

        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new NotFoundException("报名申请不存在"));
        ensureApplicationGroupable(application, group);

        GroupMember groupMember = GroupMember.builder()
                .groupId(group.getId())
                .userId(application.getUserId())
                .applicationId(application.getId())
                .build();
        application.setStatus(ApplicationStatus.GROUPED);
        application.setStatusRemark(null);

        try {
            groupMemberRepository.save(groupMember);
            applicationRepository.save(application);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("该报名申请已被其他操作分组，请刷新后重试");
        }
        notificationService.createOrRefresh(new NotificationCommand(
                application.getUserId(),
                currentUser.getUserId(),
                NotificationType.APPLICATION_GROUPED,
                "报名申请已完成分组",
                "你的报名申请已被分配到分组：" + group.getName(),
                "application.grouped:" + application.getId() + ":" + group.getId(),
                "APPLICATION",
                application.getId()
        ));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("groupId", group.getId());
        detail.put("groupName", group.getName());
        detail.put("userId", application.getUserId());
        detail.put("applicationId", application.getId());
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.GROUP,
                        "ASSIGN_APPLICATION_TO_GROUP",
                        AuditSeverity.IMPORTANT,
                        "分配报名申请到分组"
                ).actor(currentUser)
                .target("GROUP", group.getId())
                .detail(detail)
                .build());
    }

    @Transactional(readOnly = true)
    public GroupDetailVo getGroupDetail(Long groupId) {
        RecruitmentGroup group = recruitmentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("分组不存在"));
        return buildGroupDetailVo(group);
    }

    @Transactional(readOnly = true)
    public List<GroupMemberVo> listGroupMembers(Long groupId) {
        RecruitmentGroup group = recruitmentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("分组不存在"));
        List<GroupMember> groupMembers = groupMemberRepository.findAllByGroupId(groupId);
        if (groupMembers.isEmpty()) {
            return List.of();
        }

        List<Long> applicationIds = groupMembers.stream().map(GroupMember::getApplicationId).toList();
        Map<Long, Application> applicationMap = applicationRepository.findAllById(applicationIds).stream()
                .collect(Collectors.toMap(Application::getId, Function.identity()));
        Set<Long> userIds = groupMembers.stream().map(GroupMember::getUserId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Direction> directionMap = loadDirectionsForApplications(applicationMap.values());

        return groupMembers.stream()
                .map(groupMember -> {
                    Application application = applicationMap.get(groupMember.getApplicationId());
                    User user = userMap.get(groupMember.getUserId());
                    Direction level1 = application == null ? null : directionMap.get(application.getDirectionLevel1Id());
                    Direction level2 = application == null ? null : directionMap.get(application.getDirectionLevel2Id());
                    return new GroupMemberVo(
                            groupMember.getUserId(),
                            user == null ? null : user.getUsername(),
                            application == null ? null : application.getRealName(),
                            groupMember.getApplicationId(),
                            application == null ? group.getGrade() : application.getGrade(),
                            application == null ? group.getAdmissionYear() : application.getAdmissionYear(),
                            level1 == null ? null : level1.getName(),
                            level2 == null ? null : level2.getName(),
                            application == null ? ApplicationStatus.GROUPED : application.getStatus()
                    );
                })
                .toList();
    }

    private void ensureCanManageGroup(LoginUser currentUser, RecruitmentGroup group) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() != Role.LEADER || !Objects.equals(group.getLeaderUserId(), currentUser.getUserId())) {
            throw new ForbiddenException("无权操作该分组");
        }
    }

    private void ensureApplicationGroupable(Application application, RecruitmentGroup group) {
        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new ConflictException("当前报名申请状态不允许分组");
        }
        if (groupMemberRepository.findByApplicationId(application.getId()).isPresent()) {
            throw new ConflictException("该报名申请已完成分组");
        }
        if (!Objects.equals(application.getDirectionLevel1Id(), group.getDirectionLevel1Id())
                || !Objects.equals(application.getDirectionLevel2Id(), group.getDirectionLevel2Id())
                || application.getGrade() != group.getGrade()
                || !Objects.equals(application.getAdmissionYear(), group.getAdmissionYear())) {
            throw new ConflictException("报名申请与目标分组条件不匹配");
        }
        long currentSize = groupMemberRepository.countByGroupIdForUpdate(group.getId());
        if (currentSize >= group.getMaxSize()) {
            throw new ConflictException("目标分组人数已满");
        }
    }

    private List<ManageableGroupVo> buildManageableGroupVos(List<RecruitmentGroup> groups) {
        if (groups.isEmpty()) {
            return List.of();
        }
        Map<Long, Direction> directionMap = loadDirectionsForGroups(groups);
        Map<Long, Long> currentSizeMap = groupMemberRepository.findAllByGroupIdIn(
                        groups.stream().map(RecruitmentGroup::getId).toList()
                ).stream()
                .collect(Collectors.groupingBy(GroupMember::getGroupId, Collectors.counting()));

        return groups.stream()
                .map(group -> toManageableGroupVo(group, directionMap, currentSizeMap.getOrDefault(group.getId(), 0L)))
                .toList();
    }

    private GroupDetailVo buildGroupDetailVo(RecruitmentGroup group) {
        Map<Long, Direction> directionMap = loadDirectionsForGroups(List.of(group));
        long currentSize = groupMemberRepository.countByGroupId(group.getId());
        Direction level1 = directionMap.get(group.getDirectionLevel1Id());
        Direction level2 = directionMap.get(group.getDirectionLevel2Id());
        return new GroupDetailVo(
                group.getId(),
                group.getName(),
                group.getDirectionLevel1Id(),
                level1 == null ? null : level1.getName(),
                group.getDirectionLevel2Id(),
                level2 == null ? null : level2.getName(),
                group.getGrade(),
                group.getAdmissionYear(),
                group.getMaxSize(),
                currentSize,
                group.getLeaderUserId(),
                toOffsetDateTime(group.getCreatedAt()),
                toOffsetDateTime(group.getUpdatedAt())
        );
    }

    private ManageableGroupVo toManageableGroupVo(RecruitmentGroup group, Map<Long, Direction> directionMap, long currentSize) {
        Direction level1 = directionMap.get(group.getDirectionLevel1Id());
        Direction level2 = directionMap.get(group.getDirectionLevel2Id());
        return new ManageableGroupVo(
                group.getId(),
                group.getName(),
                group.getDirectionLevel1Id(),
                level1 == null ? null : level1.getName(),
                group.getDirectionLevel2Id(),
                level2 == null ? null : level2.getName(),
                group.getGrade(),
                group.getAdmissionYear(),
                group.getMaxSize(),
                currentSize,
                group.getLeaderUserId(),
                toOffsetDateTime(group.getCreatedAt()),
                toOffsetDateTime(group.getUpdatedAt())
        );
    }

    private List<UngroupedApplicationVo> buildUngroupedApplicationVos(List<Application> applications) {
        if (applications.isEmpty()) {
            return List.of();
        }
        Map<Long, User> userMap = userRepository.findAllById(
                        applications.stream().map(Application::getUserId).collect(Collectors.toCollection(LinkedHashSet::new))
                ).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Direction> directionMap = loadDirectionsForApplications(applications);

        return applications.stream()
                .map(application -> {
                    User user = userMap.get(application.getUserId());
                    Direction level1 = directionMap.get(application.getDirectionLevel1Id());
                    Direction level2 = directionMap.get(application.getDirectionLevel2Id());
                    return new UngroupedApplicationVo(
                            application.getId(),
                            application.getUserId(),
                            user == null ? null : user.getUsername(),
                            user == null ? null : user.getEmail(),
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
                            toOffsetDateTime(application.getCreatedAt()),
                            toOffsetDateTime(application.getUpdatedAt())
                    );
                })
                .toList();
    }

    private Map<Long, Direction> loadDirectionsForGroups(Collection<RecruitmentGroup> groups) {
        Set<Long> directionIds = new LinkedHashSet<>();
        for (RecruitmentGroup group : groups) {
            directionIds.add(group.getDirectionLevel1Id());
            directionIds.add(group.getDirectionLevel2Id());
        }
        return directionRepository.findAllById(directionIds).stream()
                .collect(Collectors.toMap(Direction::getId, Function.identity()));
    }

    private Map<Long, Direction> loadDirectionsForApplications(Collection<Application> applications) {
        Set<Long> directionIds = new LinkedHashSet<>();
        for (Application application : applications) {
            directionIds.add(application.getDirectionLevel1Id());
            directionIds.add(application.getDirectionLevel2Id());
        }
        return directionRepository.findAllById(directionIds).stream()
                .collect(Collectors.toMap(Direction::getId, Function.identity()));
    }

    private OffsetDateTime toOffsetDateTime(java.time.LocalDateTime value) {
        return value.atZone(appClock.getZone()).toOffsetDateTime();
    }
}
