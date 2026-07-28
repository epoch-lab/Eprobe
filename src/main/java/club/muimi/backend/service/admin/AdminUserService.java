package club.muimi.backend.service.admin;

import club.muimi.backend.common.api.PageResult;
import club.muimi.backend.common.enums.AuditModule;
import club.muimi.backend.common.enums.AuditSeverity;
import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.dto.admin.UpdateUserRoleRequest;
import club.muimi.backend.dto.admin.UpdateUserStatusRequest;
import club.muimi.backend.entity.Application;
import club.muimi.backend.entity.GroupMember;
import club.muimi.backend.entity.RecruitmentGroup;
import club.muimi.backend.entity.User;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.exception.NotFoundException;
import club.muimi.backend.exception.ValidationException;
import club.muimi.backend.repository.ApplicationRepository;
import club.muimi.backend.repository.GroupMemberRepository;
import club.muimi.backend.repository.RecruitmentGroupRepository;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogCommand;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.vo.admin.AdminUserDetailVo;
import club.muimi.backend.vo.admin.AdminUserSummaryVo;
import club.muimi.backend.vo.auth.GroupSimpleVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final RecruitmentGroupRepository recruitmentGroupRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public AdminUserService(
            UserRepository userRepository,
            ApplicationRepository applicationRepository,
            GroupMemberRepository groupMemberRepository,
            RecruitmentGroupRepository recruitmentGroupRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.recruitmentGroupRepository = recruitmentGroupRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public PageResult<AdminUserSummaryVo> listUsers(
            int page,
            int size,
            Role role,
            UserStatus status,
            String keyword
    ) {
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = userRepository.searchUsers(role, status, normalizeKeyword(keyword), pageable);
        List<User> users = result.getContent();
        List<Long> userIds = users.stream().map(User::getId).toList();

        Map<Long, Long> applicationCountMap = buildApplicationCountMap(userIds);
        Map<Long, Long> groupCountMap = buildGroupCountMap(userIds);
        Map<Long, Long> leaderGroupCountMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : recruitmentGroupRepository.findAllByLeaderUserIdIn(userIds)
                .stream()
                .collect(Collectors.groupingBy(RecruitmentGroup::getLeaderUserId, Collectors.counting()));

        Page<AdminUserSummaryVo> mappedPage = result.map(user -> new AdminUserSummaryVo(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                Boolean.TRUE.equals(user.getEmailVerified()),
                leaderGroupCountMap.getOrDefault(user.getId(), 0L),
                applicationCountMap.getOrDefault(user.getId(), 0L),
                groupCountMap.getOrDefault(user.getId(), 0L),
                user.getLastLoginAt(),
                user.getCreatedAt()
        ));
        return PageResult.from(mappedPage);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailVo getUserDetail(Long userId) {
        User user = getUserOrThrow(userId);
        return toDetailVo(user);
    }

    @Transactional
    public AdminUserDetailVo updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        if (currentUser.getUserId().equals(userId)) {
            throw new ForbiddenException("管理员不能修改自己的状态");
        }

        User user = getUserOrThrow(userId);
        UserStatus previousStatus = user.getStatus();
        user.setStatus(request.status());
        userRepository.save(user);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("previousStatus", previousStatus);
        detail.put("currentStatus", request.status());
        detail.put("targetRole", user.getRole());
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.AUTH,
                        "UPDATE_USER_STATUS",
                        request.status() == UserStatus.DISABLED ? AuditSeverity.MAJOR : AuditSeverity.IMPORTANT,
                        "更新用户状态"
                ).actor(currentUser)
                .target("USER", userId)
                .detail(detail)
                .build());
        return toDetailVo(user);
    }

    @Transactional
    public AdminUserDetailVo updateUserRole(Long userId, UpdateUserRoleRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        if (currentUser.getUserId().equals(userId)) {
            throw new ForbiddenException("管理员不能修改自己的角色");
        }
        validateManagedRole(request.role());

        User user = getUserOrThrow(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new ForbiddenException("管理员账号角色不允许通过该接口修改");
        }
        Role previousRole = user.getRole();
        if (previousRole == request.role()) {
            return toDetailVo(user);
        }
        if (previousRole == Role.LEADER && request.role() == Role.FRESHMAN
                && recruitmentGroupRepository.findAllByLeaderUserId(userId).stream().findAny().isPresent()) {
            throw new ConflictException("该负责人仍绑定负责的分组，不能降级为新生");
        }

        user.setRole(request.role());
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("previousRole", previousRole);
        detail.put("currentRole", request.role());
        detail.put("tokenVersion", user.getTokenVersion());
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.AUTH,
                        "UPDATE_USER_ROLE",
                        AuditSeverity.IMPORTANT,
                        "更新用户角色"
                ).actor(currentUser)
                .target("USER", userId)
                .detail(detail)
                .build());
        return toDetailVo(user);
    }

    private AdminUserDetailVo toDetailVo(User user) {
        List<GroupMember> groupMembers = groupMemberRepository.findAllByUserId(user.getId());
        Set<Long> groupIds = groupMembers.stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toSet());
        List<GroupSimpleVo> groups = groupIds.isEmpty()
                ? List.of()
                : recruitmentGroupRepository.findAllByIdIn(groupIds)
                .stream()
                .map(group -> new GroupSimpleVo(group.getId(), group.getName()))
                .toList();
        List<GroupSimpleVo> leaderGroups = recruitmentGroupRepository.findAllByLeaderUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(group -> new GroupSimpleVo(group.getId(), group.getName()))
                .toList();

        return new AdminUserDetailVo(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                Boolean.TRUE.equals(user.getEmailVerified()),
                leaderGroups,
                applicationRepository.countByUserId(user.getId()),
                groupMembers.size(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                groups
        );
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
    }

    private void validateManagedRole(Role role) {
        if (role != Role.FRESHMAN && role != Role.LEADER) {
            throw new ValidationException("用户角色只能在 FRESHMAN 与 LEADER 之间调整");
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<Long, Long> buildApplicationCountMap(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return applicationRepository.findAllByUserIdIn(userIds)
                .stream()
                .collect(Collectors.groupingBy(Application::getUserId, Collectors.counting()));
    }

    private Map<Long, Long> buildGroupCountMap(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return groupMemberRepository.findAllByUserIdIn(userIds)
                .stream()
                .collect(Collectors.groupingBy(GroupMember::getUserId, Collectors.counting()));
    }
}
