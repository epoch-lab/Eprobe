package club.muimi.backend.service.announcement;

import club.muimi.backend.common.enums.AnnouncementScope;
import club.muimi.backend.common.enums.AuditModule;
import club.muimi.backend.common.enums.AuditSeverity;
import club.muimi.backend.common.enums.NotificationType;
import club.muimi.backend.dto.announcement.UpsertAnnouncementRequest;
import club.muimi.backend.entity.Announcement;
import club.muimi.backend.entity.GroupMember;
import club.muimi.backend.entity.RecruitmentGroup;
import club.muimi.backend.entity.User;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.exception.NotFoundException;
import club.muimi.backend.repository.AnnouncementRepository;
import club.muimi.backend.repository.GroupMemberRepository;
import club.muimi.backend.repository.RecruitmentGroupRepository;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogCommand;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.notification.NotificationCommand;
import club.muimi.backend.service.notification.NotificationService;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.vo.announcement.AnnouncementVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final RecruitmentGroupRepository recruitmentGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final Clock appClock;

    public AnnouncementService(
            AnnouncementRepository announcementRepository,
            RecruitmentGroupRepository recruitmentGroupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            Clock appClock
    ) {
        this.announcementRepository = announcementRepository;
        this.recruitmentGroupRepository = recruitmentGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.appClock = appClock;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementVo> listVisibleAnnouncements() {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        List<Announcement> announcements = loadVisibleAnnouncements(currentUser);
        return buildAnnouncementVos(announcements);
    }

    @Transactional(readOnly = true)
    public AnnouncementVo getAnnouncement(Long announcementId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new NotFoundException("公告不存在"));
        ensureCanViewAnnouncement(currentUser, announcement);
        return buildAnnouncementVos(List.of(announcement)).getFirst();
    }

    @Transactional
    public AnnouncementVo createAnnouncement(UpsertAnnouncementRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        Long groupId = validateScopeAndGroup(currentUser, request.scope(), request.groupId());
        Announcement announcement = announcementRepository.save(Announcement.builder()
                .title(request.title().trim())
                .contentMarkdown(request.contentMarkdown().trim())
                .scope(request.scope())
                .groupId(groupId)
                .publisherUserId(currentUser.getUserId())
                .build());
        notifyAnnouncementPublished(currentUser, announcement);
        recordAnnouncementAudit("CREATE_ANNOUNCEMENT", "创建公告", currentUser, announcement);
        return buildAnnouncementVos(List.of(announcement)).getFirst();
    }

    @Transactional
    public AnnouncementVo updateAnnouncement(Long announcementId, UpsertAnnouncementRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new NotFoundException("公告不存在"));
        ensureCanManageAnnouncement(currentUser, announcement);
        Long groupId = validateScopeAndGroup(currentUser, request.scope(), request.groupId());
        ensureVisibilityUnchanged(announcement, request.scope(), groupId);
        announcement.setTitle(request.title().trim());
        announcement.setContentMarkdown(request.contentMarkdown().trim());
        announcement.setScope(request.scope());
        announcement.setGroupId(groupId);
        Announcement saved = announcementRepository.save(announcement);
        notifyAnnouncementPublished(currentUser, saved);
        recordAnnouncementAudit("UPDATE_ANNOUNCEMENT", "更新公告", currentUser, saved);
        return buildAnnouncementVos(List.of(saved)).getFirst();
    }

    @Transactional
    public void deleteAnnouncement(Long announcementId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new NotFoundException("公告不存在"));
        ensureCanManageAnnouncement(currentUser, announcement);
        announcementRepository.delete(announcement);
        recordAnnouncementAudit("DELETE_ANNOUNCEMENT", "删除公告", currentUser, announcement);
    }

    private List<Announcement> loadVisibleAnnouncements(LoginUser currentUser) {
        if (currentUser.getRole().name().equals("ADMIN")) {
            return announcementRepository.findAllByOrderByCreatedAtDesc();
        }
        Set<Long> visibleGroupIds = new LinkedHashSet<>();
        visibleGroupIds.addAll(groupMemberRepository.findAllByUserId(currentUser.getUserId()).stream().map(GroupMember::getGroupId).toList());
        visibleGroupIds.addAll(recruitmentGroupRepository.findAllByLeaderUserId(currentUser.getUserId()).stream().map(RecruitmentGroup::getId).toList());

        List<Announcement> allAnnouncements = announcementRepository.findAllByOrderByCreatedAtDesc();
        return allAnnouncements.stream()
                .filter(announcement -> announcement.getScope() == AnnouncementScope.GLOBAL
                        || (announcement.getGroupId() != null && visibleGroupIds.contains(announcement.getGroupId())))
                .toList();
    }

    private Long validateScopeAndGroup(LoginUser currentUser, AnnouncementScope scope, Long groupId) {
        if (scope == AnnouncementScope.GLOBAL) {
            if (!currentUser.getRole().name().equals("ADMIN")) {
                throw new ForbiddenException("只有管理员可以发布全局公告");
            }
            return null;
        }
        if (groupId == null) {
            throw new ConflictException("组内公告必须指定分组");
        }
        RecruitmentGroup group = recruitmentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("分组不存在"));
        if (currentUser.getRole().name().equals("ADMIN")) {
            return group.getId();
        }
        if (!Objects.equals(group.getLeaderUserId(), currentUser.getUserId())) {
            throw new ForbiddenException("当前用户无权操作该分组公告");
        }
        return group.getId();
    }

    private void ensureCanViewAnnouncement(LoginUser currentUser, Announcement announcement) {
        if (currentUser.getRole().name().equals("ADMIN")) {
            return;
        }
        if (announcement.getScope() == AnnouncementScope.GLOBAL) {
            return;
        }
        Long groupId = announcement.getGroupId();
        if (groupId == null) {
            throw new NotFoundException("公告数据异常");
        }
        if (groupMemberRepository.existsByUserIdAndGroupId(currentUser.getUserId(), groupId)) {
            return;
        }
        if (recruitmentGroupRepository.existsByIdAndLeaderUserId(groupId, currentUser.getUserId())) {
            return;
        }
        throw new ForbiddenException("当前用户无权查看该公告");
    }

    private void ensureCanManageAnnouncement(LoginUser currentUser, Announcement announcement) {
        if (currentUser.getRole().name().equals("ADMIN")) {
            return;
        }
        if (announcement.getScope() == AnnouncementScope.GLOBAL) {
            throw new ForbiddenException("负责人不能管理全局公告");
        }
        if (!recruitmentGroupRepository.existsByIdAndLeaderUserId(announcement.getGroupId(), currentUser.getUserId())) {
            throw new ForbiddenException("当前用户无权管理该公告");
        }
    }

    private void ensureVisibilityUnchanged(Announcement announcement, AnnouncementScope scope, Long groupId) {
        if (announcement.getScope() != scope || !Objects.equals(announcement.getGroupId(), groupId)) {
            throw new ConflictException("公告发布后不允许修改可见范围");
        }
    }

    private List<AnnouncementVo> buildAnnouncementVos(List<Announcement> announcements) {
        if (announcements.isEmpty()) {
            return List.of();
        }
        Set<Long> groupIds = announcements.stream()
                .map(Announcement::getGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, RecruitmentGroup> groupMap = groupIds.isEmpty()
                ? Map.of()
                : recruitmentGroupRepository.findAllByIdIn(groupIds).stream()
                .collect(Collectors.toMap(RecruitmentGroup::getId, Function.identity()));
        Map<Long, User> userMap = userRepository.findAllById(
                        announcements.stream().map(Announcement::getPublisherUserId).collect(Collectors.toCollection(LinkedHashSet::new))
                ).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return announcements.stream()
                .map(announcement -> {
                    RecruitmentGroup group = announcement.getGroupId() == null ? null : groupMap.get(announcement.getGroupId());
                    User publisher = userMap.get(announcement.getPublisherUserId());
                    return new AnnouncementVo(
                            announcement.getId(),
                            announcement.getTitle(),
                            announcement.getContentMarkdown(),
                            announcement.getScope(),
                            announcement.getGroupId(),
                            group == null ? null : group.getName(),
                            announcement.getPublisherUserId(),
                            publisher == null ? null : publisher.getUsername(),
                            announcement.getCreatedAt().atZone(appClock.getZone()).toOffsetDateTime(),
                            announcement.getUpdatedAt().atZone(appClock.getZone()).toOffsetDateTime()
                    );
                })
                .toList();
    }

    private void notifyAnnouncementPublished(LoginUser actor, Announcement announcement) {
        List<Long> recipientUserIds = announcement.getScope() == AnnouncementScope.GLOBAL
                ? notificationService.findAllActiveUserIds()
                : groupMemberRepository.findAllByGroupId(announcement.getGroupId()).stream().map(GroupMember::getUserId).toList();
        List<NotificationCommand> commands = recipientUserIds.stream()
                .filter(userId -> !Objects.equals(userId, actor.getUserId()))
                .distinct()
                .map(userId -> new NotificationCommand(
                        userId,
                        actor.getUserId(),
                        NotificationType.ANNOUNCEMENT_PUBLISHED,
                        announcement.getScope() == AnnouncementScope.GLOBAL ? "全局公告已发布" : "组内公告已发布",
                        announcement.getTitle(),
                        "announcement.published:" + announcement.getId(),
                        "ANNOUNCEMENT",
                        announcement.getId()
                ))
                .toList();
        notificationService.createOrRefreshAll(commands);
    }

    private void recordAnnouncementAudit(String action, String summary, LoginUser actor, Announcement announcement) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("scope", announcement.getScope());
        detail.put("groupId", announcement.getGroupId());
        detail.put("title", announcement.getTitle());
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.ANNOUNCEMENT,
                        action,
                        AuditSeverity.IMPORTANT,
                        summary
                ).actor(actor)
                .target("ANNOUNCEMENT", announcement.getId())
                .detail(detail)
                .build());
    }
}
