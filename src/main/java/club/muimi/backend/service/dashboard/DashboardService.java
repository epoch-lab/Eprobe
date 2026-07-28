package club.muimi.backend.service.dashboard;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.TaskSubmissionStatus;
import club.muimi.backend.entity.GroupMember;
import club.muimi.backend.entity.RecruitmentGroup;
import club.muimi.backend.entity.RecruitmentTask;
import club.muimi.backend.entity.TaskSubmission;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.exception.NotFoundException;
import club.muimi.backend.repository.*;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.task.TaskService;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.vo.dashboard.DashboardOverviewVo;
import club.muimi.backend.vo.dashboard.GroupDashboardDetailVo;
import club.muimi.backend.vo.dashboard.GroupDashboardSummaryVo;
import club.muimi.backend.vo.task.ManageTaskVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final RecruitmentGroupRepository recruitmentGroupRepository;
    private final RecruitmentTaskRepository recruitmentTaskRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final TaskService taskService;

    public DashboardService(
            CurrentUserService currentUserService,
            UserRepository userRepository,
            ApplicationRepository applicationRepository,
            GroupMemberRepository groupMemberRepository,
            RecruitmentGroupRepository recruitmentGroupRepository,
            RecruitmentTaskRepository recruitmentTaskRepository,
            TaskSubmissionRepository taskSubmissionRepository,
            TaskService taskService
    ) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.recruitmentGroupRepository = recruitmentGroupRepository;
        this.recruitmentTaskRepository = recruitmentTaskRepository;
        this.taskSubmissionRepository = taskSubmissionRepository;
        this.taskService = taskService;
    }

    @Transactional(readOnly = true)
    public DashboardOverviewVo getAdminOverview() {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new ForbiddenException("只有管理员可以查看全局总览");
        }
        long totalUsers = userRepository.count();
        long totalApplications = applicationRepository.count();
        long groupedApplications = groupMemberRepository.count();
        long ungroupedApplications = applicationRepository.findAllUngroupedSubmittedApplications(org.springframework.data.domain.Sort.unsorted()).size();
        long totalGroups = recruitmentGroupRepository.count();
        long totalTasks = recruitmentTaskRepository.count();
        List<TaskSubmission> submissions = taskSubmissionRepository.findAll();
        long totalSubmitted = submissions.stream().filter(submission -> submission.getStatus() == TaskSubmissionStatus.SUBMITTED).count();
        long totalReviewed = submissions.stream().filter(submission -> submission.getStatus() == TaskSubmissionStatus.REVIEWED).count();
        return new DashboardOverviewVo(
                totalUsers,
                totalApplications,
                groupedApplications,
                ungroupedApplications,
                totalGroups,
                totalTasks,
                totalSubmitted,
                totalReviewed
        );
    }

    @Transactional(readOnly = true)
    public List<GroupDashboardSummaryVo> listManageableGroupSummaries() {
        List<RecruitmentGroup> groups = loadManageableGroups();
        return buildGroupSummaries(groups);
    }

    @Transactional(readOnly = true)
    public GroupDashboardDetailVo getManageableGroupDetail(Long groupId) {
        RecruitmentGroup group = loadManageableGroups().stream()
                .filter(candidate -> candidate.getId().equals(groupId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("分组不存在或当前用户无权查看"));
        GroupDashboardSummaryVo summary = buildGroupSummaries(List.of(group)).getFirst();
        List<ManageTaskVo> tasks = taskService.listManageableTasks(groupId);
        return new GroupDashboardDetailVo(
                group.getId(),
                group.getName(),
                summary.memberCount(),
                summary.taskCount(),
                summary.submittedCount(),
                summary.reviewedCount(),
                summary.pendingCount(),
                summary.completionRate(),
                tasks
        );
    }

    private List<RecruitmentGroup> loadManageableGroups() {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        return switch (currentUser.getRole()) {
            case ADMIN -> recruitmentGroupRepository.findAllByOrderByCreatedAtDesc();
            case LEADER ->
                    recruitmentGroupRepository.findAllByLeaderUserIdOrderByCreatedAtDesc(currentUser.getUserId());
            default -> throw new ForbiddenException("当前用户无权查看分组看板");
        };
    }

    private List<GroupDashboardSummaryVo> buildGroupSummaries(List<RecruitmentGroup> groups) {
        if (groups.isEmpty()) {
            return List.of();
        }
        Set<Long> groupIds = groups.stream().map(RecruitmentGroup::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<GroupMember> groupMembers = groupMemberRepository.findAllByGroupIdIn(groupIds);
        Map<Long, Long> memberCountMap = groupMembers.stream()
                .collect(Collectors.groupingBy(GroupMember::getGroupId, Collectors.counting()));
        Map<Long, Set<Long>> memberUserIdsByGroup = groupMembers.stream()
                .collect(Collectors.groupingBy(
                        GroupMember::getGroupId,
                        Collectors.mapping(GroupMember::getUserId, Collectors.toSet())
                ));
        List<RecruitmentTask> tasks = recruitmentTaskRepository.findAllByGroupIdInOrderByCreatedAtDesc(groupIds);
        Map<Long, List<RecruitmentTask>> taskMap = tasks.stream().collect(Collectors.groupingBy(RecruitmentTask::getGroupId));
        List<Long> taskIds = tasks.stream().map(RecruitmentTask::getId).toList();
        Map<Long, List<TaskSubmission>> submissionMap = taskIds.isEmpty()
                ? Map.of()
                : taskSubmissionRepository.findAllByTaskIdIn(taskIds).stream()
                .collect(Collectors.groupingBy(TaskSubmission::getTaskId));

        return groups.stream()
                .map(group -> {
                    long memberCount = memberCountMap.getOrDefault(group.getId(), 0L);
                    Set<Long> memberUserIds = memberUserIdsByGroup.getOrDefault(group.getId(), Set.of());
                    List<RecruitmentTask> groupTasks = taskMap.getOrDefault(group.getId(), List.of());
                    long taskCount = groupTasks.size();
                    long submittedCount = groupTasks.stream()
                            .flatMap(task -> submissionMap.getOrDefault(task.getId(), List.of()).stream())
                            .filter(submission -> memberUserIds.contains(submission.getUserId()))
                            .filter(submission -> submission.getStatus() == TaskSubmissionStatus.SUBMITTED)
                            .count();
                    long reviewedCount = groupTasks.stream()
                            .flatMap(task -> submissionMap.getOrDefault(task.getId(), List.of()).stream())
                            .filter(submission -> memberUserIds.contains(submission.getUserId()))
                            .filter(submission -> submission.getStatus() == TaskSubmissionStatus.REVIEWED)
                            .count();
                    long pendingCount = Math.max(0L, memberCount * taskCount - submittedCount - reviewedCount);
                    double completionRate = memberCount == 0 || taskCount == 0
                            ? 0D
                            : (double) (submittedCount + reviewedCount) / (memberCount * taskCount);
                    return new GroupDashboardSummaryVo(
                            group.getId(),
                            group.getName(),
                            memberCount,
                            taskCount,
                            submittedCount,
                            reviewedCount,
                            pendingCount,
                            completionRate
                    );
                })
                .toList();
    }
}
