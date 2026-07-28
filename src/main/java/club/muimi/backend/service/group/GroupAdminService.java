package club.muimi.backend.service.group;

import club.muimi.backend.common.enums.*;
import club.muimi.backend.dto.admin.AssignGroupLeaderRequest;
import club.muimi.backend.dto.admin.GroupUpsertRequest;
import club.muimi.backend.entity.Direction;
import club.muimi.backend.entity.GroupMember;
import club.muimi.backend.entity.RecruitmentGroup;
import club.muimi.backend.entity.User;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.NotFoundException;
import club.muimi.backend.exception.ValidationException;
import club.muimi.backend.repository.DirectionRepository;
import club.muimi.backend.repository.GroupMemberRepository;
import club.muimi.backend.repository.RecruitmentGroupRepository;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogCommand;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.vo.group.GroupDetailVo;
import club.muimi.backend.vo.group.ManageableGroupVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroupAdminService {

    private final RecruitmentGroupRepository recruitmentGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final DirectionRepository directionRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final Clock appClock;

    public GroupAdminService(
            RecruitmentGroupRepository recruitmentGroupRepository,
            GroupMemberRepository groupMemberRepository,
            DirectionRepository directionRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            Clock appClock
    ) {
        this.recruitmentGroupRepository = recruitmentGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.directionRepository = directionRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.appClock = appClock;
    }

    @Transactional(readOnly = true)
    public List<ManageableGroupVo> listAllGroups() {
        List<RecruitmentGroup> groups = recruitmentGroupRepository.findAllByOrderByCreatedAtDesc();
        return buildManageableGroupVos(groups);
    }

    @Transactional(readOnly = true)
    public GroupDetailVo getGroupDetail(Long groupId) {
        RecruitmentGroup group = getGroupOrThrow(groupId);
        return buildGroupDetailVo(group);
    }

    @Transactional
    public GroupDetailVo createGroup(GroupUpsertRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        DirectionSelection directionSelection = validateDirectionSelection(
                request.directionLevel1Id(),
                request.directionLevel2Id()
        );
        String normalizedName = normalizeName(request.name());
        ensureGroupNameUnique(normalizedName, null);

        RecruitmentGroup group = RecruitmentGroup.builder()
                .name(normalizedName)
                .directionLevel1Id(directionSelection.level1().getId())
                .directionLevel2Id(directionSelection.level2().getId())
                .grade(request.grade())
                .admissionYear(request.admissionYear())
                .maxSize(request.maxSize())
                .leaderUserId(null)
                .build();
        RecruitmentGroup saved = recruitmentGroupRepository.save(group);
        recordGroupAudit("CREATE_GROUP", "创建分组", currentUser, saved, null);
        return buildGroupDetailVo(saved);
    }

    @Transactional
    public GroupDetailVo updateGroup(Long groupId, GroupUpsertRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        RecruitmentGroup group = getGroupOrThrow(groupId);
        DirectionSelection directionSelection = validateDirectionSelection(
                request.directionLevel1Id(),
                request.directionLevel2Id()
        );
        String normalizedName = normalizeName(request.name());
        ensureGroupNameUnique(normalizedName, groupId);

        long currentSize = groupMemberRepository.countByGroupId(groupId);
        if (request.maxSize() < currentSize) {
            throw new ConflictException("分组人数上限不能小于当前已分组人数");
        }
        if (currentSize > 0 && isMembershipDimensionChanged(group, directionSelection, request.grade(), request.admissionYear())) {
            throw new ConflictException("分组内已有成员时，不允许修改方向、年级或入学年份");
        }

        group.setName(normalizedName);
        group.setDirectionLevel1Id(directionSelection.level1().getId());
        group.setDirectionLevel2Id(directionSelection.level2().getId());
        group.setGrade(request.grade());
        group.setAdmissionYear(request.admissionYear());
        group.setMaxSize(request.maxSize());

        RecruitmentGroup saved = recruitmentGroupRepository.save(group);
        recordGroupAudit("UPDATE_GROUP", "更新分组", currentUser, saved, null);
        return buildGroupDetailVo(saved);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        RecruitmentGroup group = getGroupOrThrow(groupId);
        if (groupMemberRepository.countByGroupId(groupId) > 0) {
            throw new ConflictException("分组内仍有成员，暂不允许删除");
        }
        recruitmentGroupRepository.delete(group);
        recordGroupAudit("DELETE_GROUP", "删除分组", currentUser, group, null);
    }

    @Transactional
    public GroupDetailVo assignLeader(Long groupId, AssignGroupLeaderRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        RecruitmentGroup group = getGroupOrThrow(groupId);
        User leader = userRepository.findById(request.leaderUserId())
                .orElseThrow(() -> new NotFoundException("负责人用户不存在"));
        if (leader.getRole() != Role.LEADER) {
            throw new ConflictException("只能指派角色为 LEADER 的用户作为分组负责人");
        }
        if (leader.getStatus() != UserStatus.ACTIVE) {
            throw new ConflictException("当前负责人用户已被禁用，暂不允许指派");
        }
        group.setLeaderUserId(leader.getId());
        RecruitmentGroup saved = recruitmentGroupRepository.save(group);
        recordGroupAudit("ASSIGN_GROUP_LEADER", "指派分组负责人", currentUser, saved, leader.getId());
        return buildGroupDetailVo(saved);
    }

    @Transactional
    public GroupDetailVo removeLeader(Long groupId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        RecruitmentGroup group = getGroupOrThrow(groupId);
        Long previousLeaderUserId = group.getLeaderUserId();
        group.setLeaderUserId(null);
        RecruitmentGroup saved = recruitmentGroupRepository.save(group);
        recordGroupAudit("REMOVE_GROUP_LEADER", "移除分组负责人", currentUser, saved, previousLeaderUserId);
        return buildGroupDetailVo(saved);
    }

    private RecruitmentGroup getGroupOrThrow(Long groupId) {
        return recruitmentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("分组不存在"));
    }

    private void ensureGroupNameUnique(String normalizedName, Long currentGroupId) {
        boolean duplicated = currentGroupId == null
                ? recruitmentGroupRepository.existsByNameIgnoreCase(normalizedName)
                : recruitmentGroupRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, currentGroupId);
        if (duplicated) {
            throw new ConflictException("分组名称已存在");
        }
    }

    private boolean isMembershipDimensionChanged(
            RecruitmentGroup group,
            DirectionSelection directionSelection,
            Grade grade,
            Integer admissionYear
    ) {
        return !group.getDirectionLevel1Id().equals(directionSelection.level1().getId())
                || !group.getDirectionLevel2Id().equals(directionSelection.level2().getId())
                || group.getGrade() != grade
                || !group.getAdmissionYear().equals(admissionYear);
    }

    private DirectionSelection validateDirectionSelection(Long level1Id, Long level2Id) {
        Direction level1 = directionRepository.findById(level1Id)
                .orElseThrow(() -> new ValidationException("所选一级方向不存在"));
        Direction level2 = directionRepository.findById(level2Id)
                .orElseThrow(() -> new ValidationException("所选二级方向不存在"));

        if (level1.getLevel() != 1) {
            throw new ValidationException("所选一级方向不存在");
        }
        if (level2.getLevel() != 2) {
            throw new ValidationException("所选二级方向不存在");
        }
        if (!level1.getId().equals(level2.getParentId())) {
            throw new ValidationException("二级方向不属于所选一级方向");
        }
        return new DirectionSelection(level1, level2);
    }

    private String normalizeName(String name) {
        return name.trim();
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
                .map(group -> {
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
                            currentSizeMap.getOrDefault(group.getId(), 0L),
                            group.getLeaderUserId(),
                            toOffsetDateTime(group.getCreatedAt()),
                            toOffsetDateTime(group.getUpdatedAt())
                    );
                })
                .toList();
    }

    private GroupDetailVo buildGroupDetailVo(RecruitmentGroup group) {
        Map<Long, Direction> directionMap = loadDirectionsForGroups(List.of(group));
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
                groupMemberRepository.countByGroupId(group.getId()),
                group.getLeaderUserId(),
                toOffsetDateTime(group.getCreatedAt()),
                toOffsetDateTime(group.getUpdatedAt())
        );
    }

    private Map<Long, Direction> loadDirectionsForGroups(Collection<RecruitmentGroup> groups) {
        Set<Long> directionIds = new LinkedHashSet<>();
        for (RecruitmentGroup group : groups) {
            directionIds.add(group.getDirectionLevel1Id());
            directionIds.add(group.getDirectionLevel2Id());
        }
        return directionRepository.findAllById(directionIds).stream()
                .collect(Collectors.toMap(Direction::getId, direction -> direction));
    }

    private OffsetDateTime toOffsetDateTime(java.time.LocalDateTime value) {
        return value.atZone(appClock.getZone()).toOffsetDateTime();
    }

    private void recordGroupAudit(
            String action,
            String summary,
            LoginUser actor,
            RecruitmentGroup group,
            Long leaderUserId
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("groupName", group.getName());
        detail.put("directionLevel1Id", group.getDirectionLevel1Id());
        detail.put("directionLevel2Id", group.getDirectionLevel2Id());
        detail.put("grade", group.getGrade());
        detail.put("admissionYear", group.getAdmissionYear());
        detail.put("maxSize", group.getMaxSize());
        detail.put("leaderUserId", leaderUserId == null ? group.getLeaderUserId() : leaderUserId);
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.GROUP,
                        action,
                        AuditSeverity.IMPORTANT,
                        summary
                ).actor(actor)
                .target("GROUP", group.getId())
                .detail(detail)
                .build());
    }

    private record DirectionSelection(Direction level1, Direction level2) {
    }
}
