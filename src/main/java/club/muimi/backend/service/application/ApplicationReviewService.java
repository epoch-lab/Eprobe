package club.muimi.backend.service.application;

import club.muimi.backend.common.enums.*;
import club.muimi.backend.dto.admin.RejectApplicationRequest;
import club.muimi.backend.dto.admin.UnassignGroupApplicationRequest;
import club.muimi.backend.entity.Application;
import club.muimi.backend.entity.GroupMember;
import club.muimi.backend.entity.RecruitmentGroup;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.exception.NotFoundException;
import club.muimi.backend.repository.ApplicationRepository;
import club.muimi.backend.repository.GroupMemberRepository;
import club.muimi.backend.repository.RecruitmentGroupRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogCommand;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.notification.NotificationCommand;
import club.muimi.backend.service.notification.NotificationService;
import club.muimi.backend.service.period.PeriodService;
import club.muimi.backend.service.user.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ApplicationReviewService {

    private final ApplicationRepository applicationRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final RecruitmentGroupRepository recruitmentGroupRepository;
    private final CurrentUserService currentUserService;
    private final PeriodService periodService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public ApplicationReviewService(
            ApplicationRepository applicationRepository,
            GroupMemberRepository groupMemberRepository,
            RecruitmentGroupRepository recruitmentGroupRepository,
            CurrentUserService currentUserService,
            PeriodService periodService,
            NotificationService notificationService,
            AuditLogService auditLogService
    ) {
        this.applicationRepository = applicationRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.recruitmentGroupRepository = recruitmentGroupRepository;
        this.currentUserService = currentUserService;
        this.periodService = periodService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void rejectApplication(Long applicationId, RejectApplicationRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        periodService.ensureSelectionOpenForGrouping();

        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new NotFoundException("报名申请不存在"));
        ensureCanReview(currentUser, application);
        ensureRejectable(application);

        application.setStatus(ApplicationStatus.REJECTED);
        application.setStatusRemark(request.remark().trim());
        applicationRepository.save(application);
        notificationService.createOrRefresh(new NotificationCommand(
                application.getUserId(),
                currentUser.getUserId(),
                NotificationType.APPLICATION_REJECTED,
                "报名申请已被拒绝",
                buildRejectionContent(request.remark()),
                "application.rejected:" + application.getId(),
                "APPLICATION",
                application.getId()
        ));
        recordAudit(
                "REJECT_APPLICATION",
                "拒绝报名申请",
                currentUser,
                application.getId(),
                Map.of(
                        "userId", application.getUserId(),
                        "remark", request.remark().trim()
                )
        );
    }

    @Transactional
    public void unassignApplicationFromGroup(
            Long groupId,
            Long applicationId,
            UnassignGroupApplicationRequest request
    ) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        periodService.ensureSelectionOpenForGrouping();

        RecruitmentGroup group = recruitmentGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new NotFoundException("分组不存在"));
        ensureCanManageGroup(currentUser, group);

        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new NotFoundException("报名申请不存在"));
        GroupMember groupMember = groupMemberRepository.findByGroupIdAndApplicationId(groupId, applicationId)
                .orElseThrow(() -> new ConflictException("该报名申请当前不在目标分组中"));

        if (application.getStatus() != ApplicationStatus.GROUPED) {
            throw new ConflictException("当前报名申请状态不允许取消分组");
        }

        groupMemberRepository.delete(groupMember);
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setStatusRemark(normalizeNullableRemark(request.remark()));
        applicationRepository.save(application);
        notificationService.createOrRefresh(new NotificationCommand(
                application.getUserId(),
                currentUser.getUserId(),
                NotificationType.APPLICATION_UNASSIGNED,
                "报名申请已取消分组",
                buildUnassignContent(group.getName(), request.remark()),
                "application.unassigned:" + application.getId(),
                "APPLICATION",
                application.getId()
        ));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("groupId", groupId);
        detail.put("userId", application.getUserId());
        detail.put("remark", normalizeNullableRemark(request.remark()));
        recordAudit("UNASSIGN_APPLICATION_FROM_GROUP", "取消报名申请分组", currentUser, application.getId(), detail);
    }

    private void ensureRejectable(Application application) {
        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new ConflictException("当前报名申请状态不允许拒绝");
        }
        if (groupMemberRepository.findByApplicationId(application.getId()).isPresent()) {
            throw new ConflictException("已分组的申请不允许拒绝");
        }
    }

    private void ensureCanReview(LoginUser currentUser, Application application) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() != Role.LEADER) {
            throw new ForbiddenException("当前角色无权审核报名申请");
        }
        boolean manageable = recruitmentGroupRepository
                .existsByLeaderUserIdAndDirectionLevel1IdAndDirectionLevel2IdAndGradeAndAdmissionYear(
                        currentUser.getUserId(),
                        application.getDirectionLevel1Id(),
                        application.getDirectionLevel2Id(),
                        application.getGrade(),
                        application.getAdmissionYear()
                );
        if (!manageable) {
            throw new ForbiddenException("无权审核该报名申请");
        }
    }

    private void ensureCanManageGroup(LoginUser currentUser, RecruitmentGroup group) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() != Role.LEADER || !java.util.Objects.equals(group.getLeaderUserId(), currentUser.getUserId())) {
            throw new ForbiddenException("无权操作该分组");
        }
    }

    private String normalizeNullableRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String trimmed = remark.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildRejectionContent(String remark) {
        String normalizedRemark = normalizeNullableRemark(remark);
        return normalizedRemark == null ? "你的报名申请未通过，请留意后续通知。" : "你的报名申请未通过，备注：" + normalizedRemark;
    }

    private String buildUnassignContent(String groupName, String remark) {
        String normalizedRemark = normalizeNullableRemark(remark);
        if (normalizedRemark == null) {
            return "你的报名申请已从分组 " + groupName + " 中移出。";
        }
        return "你的报名申请已从分组 " + groupName + " 中移出，备注：" + normalizedRemark;
    }

    private void recordAudit(
            String action,
            String summary,
            LoginUser actor,
            Long applicationId,
            Map<String, Object> detail
    ) {
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.APPLICATION,
                        action,
                        AuditSeverity.IMPORTANT,
                        summary
                ).actor(actor)
                .target("APPLICATION", applicationId)
                .detail(detail)
                .build());
    }
}
