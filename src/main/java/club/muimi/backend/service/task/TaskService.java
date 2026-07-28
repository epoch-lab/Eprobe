package club.muimi.backend.service.task;

import club.muimi.backend.common.enums.*;
import club.muimi.backend.dto.task.ReviewTaskSubmissionRequest;
import club.muimi.backend.dto.task.SubmitTaskRequest;
import club.muimi.backend.dto.task.UpsertTaskRequest;
import club.muimi.backend.entity.*;
import club.muimi.backend.exception.ConflictException;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.exception.NotFoundException;
import club.muimi.backend.exception.ValidationException;
import club.muimi.backend.repository.*;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogCommand;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.file.FileStorageService;
import club.muimi.backend.service.notification.NotificationCommand;
import club.muimi.backend.service.notification.NotificationService;
import club.muimi.backend.service.period.PeriodService;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.vo.task.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private static final String TASK_BINDING_TYPE = "TASK";
    private static final String TASK_SUBMISSION_BINDING_TYPE = "TASK_SUBMISSION";

    private final RecruitmentTaskRepository recruitmentTaskRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final RecruitmentGroupRepository recruitmentGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final CurrentUserService currentUserService;
    private final PeriodService periodService;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final Clock appClock;

    public TaskService(
            RecruitmentTaskRepository recruitmentTaskRepository,
            TaskSubmissionRepository taskSubmissionRepository,
            RecruitmentGroupRepository recruitmentGroupRepository,
            GroupMemberRepository groupMemberRepository,
            StoredFileRepository storedFileRepository,
            UserRepository userRepository,
            ApplicationRepository applicationRepository,
            CurrentUserService currentUserService,
            PeriodService periodService,
            FileStorageService fileStorageService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            Clock appClock
    ) {
        this.recruitmentTaskRepository = recruitmentTaskRepository;
        this.taskSubmissionRepository = taskSubmissionRepository;
        this.recruitmentGroupRepository = recruitmentGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.storedFileRepository = storedFileRepository;
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.currentUserService = currentUserService;
        this.periodService = periodService;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.appClock = appClock;
    }

    @Transactional(readOnly = true)
    public List<TaskSummaryVo> listCurrentUserTasks() {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        List<GroupMember> groupMembers = groupMemberRepository.findAllByUserId(currentUser.getUserId());
        if (groupMembers.isEmpty()) {
            return List.of();
        }
        Set<Long> groupIds = groupMembers.stream().map(GroupMember::getGroupId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<RecruitmentTask> tasks = recruitmentTaskRepository.findAllByGroupIdInOrderByCreatedAtDesc(groupIds);
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, RecruitmentGroup> groupMap = recruitmentGroupRepository.findAllByIdIn(groupIds).stream()
                .collect(Collectors.toMap(RecruitmentGroup::getId, Function.identity()));
        Map<Long, TaskSubmission> submissionMap = taskSubmissionRepository.findAllByTaskIdIn(
                        tasks.stream().map(RecruitmentTask::getId).toList()
                ).stream()
                .filter(submission -> submission.getUserId().equals(currentUser.getUserId()))
                .collect(Collectors.toMap(TaskSubmission::getTaskId, Function.identity()));

        return tasks.stream()
                .map(task -> {
                    RecruitmentGroup group = groupMap.get(task.getGroupId());
                    TaskSubmission submission = submissionMap.get(task.getId());
                    return new TaskSummaryVo(
                            task.getId(),
                            task.getGroupId(),
                            group == null ? null : group.getName(),
                            task.getTitle(),
                            task.getMaxScore(),
                            toOffsetDateTime(task.getDeadlineAt()),
                            submission == null ? TaskSubmissionStatus.PENDING : submission.getStatus(),
                            submission == null ? null : toOffsetDateTime(submission.getSubmittedAt()),
                            submission == null ? null : toOffsetDateTime(submission.getReviewedAt())
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskDetailVo getTaskDetail(Long taskId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        RecruitmentTask task = recruitmentTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("任务不存在"));
        RecruitmentGroup group = recruitmentGroupRepository.findById(task.getGroupId())
                .orElseThrow(() -> new NotFoundException("分组不存在"));
        User publisher = userRepository.findById(task.getPublisherUserId()).orElse(null);
        TaskSubmission submission = taskSubmissionRepository.findByTaskIdAndUserId(taskId, currentUser.getUserId()).orElse(null);
        Map<Long, StoredFile> storedFileMap = loadStoredFileMap(task, submission == null ? List.of() : List.of(submission));
        Map<Long, User> reviewerMap = submission == null || submission.getReviewerUserId() == null
                ? Map.of()
                : userRepository.findAllById(List.of(submission.getReviewerUserId())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return new TaskDetailVo(
                task.getId(),
                task.getGroupId(),
                group.getName(),
                task.getTitle(),
                task.getContentMarkdown(),
                toAttachmentVo(findStoredFile(storedFileMap, task.getAttachmentFileId())),
                task.getMaxScore(),
                toOffsetDateTime(task.getDeadlineAt()),
                task.getPublisherUserId(),
                publisher == null ? null : publisher.getUsername(),
                toOffsetDateTime(task.getCreatedAt()),
                toOffsetDateTime(task.getUpdatedAt()),
                toTaskSubmissionVo(taskId, currentUser.getUserId(), submission, storedFileMap, reviewerMap)
        );
    }

    @Transactional(readOnly = true)
    public TaskSubmissionVo getCurrentUserSubmission(Long taskId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        recruitmentTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("任务不存在"));
        TaskSubmission submission = taskSubmissionRepository.findByTaskIdAndUserId(taskId, currentUser.getUserId()).orElse(null);
        Map<Long, StoredFile> storedFileMap = submission == null || submission.getAttachmentFileId() == null
                ? Map.of()
                : loadStoredFiles(List.of(submission.getAttachmentFileId()));
        Map<Long, User> reviewerMap = submission == null || submission.getReviewerUserId() == null
                ? Map.of()
                : userRepository.findAllById(List.of(submission.getReviewerUserId())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return toTaskSubmissionVo(taskId, currentUser.getUserId(), submission, storedFileMap, reviewerMap);
    }

    @Transactional
    public TaskSubmissionVo submitTask(Long taskId, SubmitTaskRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        periodService.ensureSelectionOpenForTaskSubmit();
        RecruitmentTask task = recruitmentTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new NotFoundException("任务不存在"));
        if (!groupMemberRepository.existsByUserIdAndGroupId(currentUser.getUserId(), task.getGroupId())) {
            throw new ForbiddenException("当前用户不属于该任务所在分组");
        }
        ensureTaskDeadlineNotPassed(task);
        validateSubmitPayload(request.contentMarkdown(), request.attachmentFileId());

        TaskSubmission submission = taskSubmissionRepository.findByTaskIdAndUserIdForUpdate(taskId, currentUser.getUserId())
                .orElse(TaskSubmission.builder()
                        .taskId(taskId)
                        .userId(currentUser.getUserId())
                        .build());
        if (submission.getStatus() != TaskSubmissionStatus.PENDING) {
            throw new ConflictException("当前任务已提交，若需重新提交请联系负责人或管理员打回");
        }

        StoredFile attachment = null;
        if (request.attachmentFileId() != null) {
            attachment = fileStorageService.requireOwnedUnboundFile(
                    request.attachmentFileId(),
                    StoredFilePurpose.TASK_SUBMISSION_ATTACHMENT,
                    currentUser.getUserId()
            );
        }
        submission.setStatus(TaskSubmissionStatus.SUBMITTED);
        submission.setContentMarkdown(blankToNull(request.contentMarkdown()));
        submission.setAttachmentFileId(attachment == null ? null : attachment.getId());
        submission.setSubmittedAt(LocalDateTime.now(appClock));
        submission.setReviewerUserId(null);
        submission.setScore(null);
        submission.setReviewComment(null);
        submission.setReviewedAt(null);

        TaskSubmission saved = taskSubmissionRepository.save(submission);
        if (attachment != null) {
            fileStorageService.bindFile(attachment, TASK_SUBMISSION_BINDING_TYPE, saved.getId());
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("taskId", taskId);
        detail.put("userId", currentUser.getUserId());
        detail.put("hasAttachment", attachment != null);
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.TASK,
                        "SUBMIT_TASK",
                        AuditSeverity.NORMAL,
                        "提交任务"
                ).actor(currentUser)
                .target("TASK", taskId)
                .detail(detail)
                .build());
        Map<Long, StoredFile> storedFileMap = attachment == null ? Map.of() : Map.of(attachment.getId(), attachment);
        return toTaskSubmissionVo(taskId, currentUser.getUserId(), saved, storedFileMap, Map.of());
    }

    @Transactional(readOnly = true)
    public List<ManageTaskVo> listManageableTasks(Long groupId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        RecruitmentGroup group = recruitmentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("分组不存在"));
        ensureCanManageGroup(currentUser, group);
        List<GroupMember> groupMembers = groupMemberRepository.findAllByGroupId(groupId);
        long memberCount = groupMembers.size();
        Set<Long> memberUserIds = groupMembers.stream().map(GroupMember::getUserId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<RecruitmentTask> tasks = recruitmentTaskRepository.findAllByGroupIdOrderByCreatedAtDesc(groupId);
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, List<TaskSubmission>> submissionMap = taskSubmissionRepository.findAllByTaskIdIn(
                        tasks.stream().map(RecruitmentTask::getId).toList()
                ).stream()
                .collect(Collectors.groupingBy(TaskSubmission::getTaskId));

        return tasks.stream()
                .map(task -> {
                    List<TaskSubmission> submissions = submissionMap.getOrDefault(task.getId(), List.of());
                    long submittedCount = submissions.stream()
                            .filter(submission -> memberUserIds.contains(submission.getUserId()))
                            .filter(submission -> submission.getStatus() == TaskSubmissionStatus.SUBMITTED)
                            .count();
                    long reviewedCount = submissions.stream()
                            .filter(submission -> memberUserIds.contains(submission.getUserId()))
                            .filter(submission -> submission.getStatus() == TaskSubmissionStatus.REVIEWED)
                            .count();
                    long pendingCount = Math.max(0, memberCount - submittedCount - reviewedCount);
                    double completionRate = memberCount == 0 ? 0D : (double) (submittedCount + reviewedCount) / memberCount;
                    return new ManageTaskVo(
                            task.getId(),
                            groupId,
                            group.getName(),
                            task.getTitle(),
                            task.getMaxScore(),
                            toOffsetDateTime(task.getDeadlineAt()),
                            memberCount,
                            pendingCount,
                            submittedCount,
                            reviewedCount,
                            completionRate,
                            toOffsetDateTime(task.getCreatedAt()),
                            toOffsetDateTime(task.getUpdatedAt())
                    );
                })
                .toList();
    }

    @Transactional
    public TaskDetailVo createTask(Long groupId, UpsertTaskRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        periodService.ensureSelectionOpenForTaskManage();
        RecruitmentGroup group = recruitmentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("分组不存在"));
        ensureCanManageGroup(currentUser, group);
        validateTaskPayload(request.contentMarkdown(), request.attachmentFileId());
        StoredFile attachment = null;
        if (request.attachmentFileId() != null) {
            attachment = fileStorageService.requireOwnedUnboundFile(
                    request.attachmentFileId(),
                    StoredFilePurpose.TASK_ATTACHMENT,
                    currentUser.getUserId()
            );
        }

        RecruitmentTask task = RecruitmentTask.builder()
                .groupId(groupId)
                .title(request.title().trim())
                .contentMarkdown(blankToNull(request.contentMarkdown()))
                .attachmentFileId(attachment == null ? null : attachment.getId())
                .maxScore(request.maxScore())
                .deadlineAt(toLocalDateTime(request.deadlineAt()))
                .publisherUserId(currentUser.getUserId())
                .build();
        ensureTaskDeadlineAfterNow(task.getDeadlineAt());
        RecruitmentTask saved = recruitmentTaskRepository.save(task);
        if (attachment != null) {
            fileStorageService.bindFile(attachment, TASK_BINDING_TYPE, saved.getId());
        }
        notifyTaskPublished(currentUser, group, saved);
        recordTaskAudit(
                "CREATE_TASK",
                AuditSeverity.IMPORTANT,
                "创建任务",
                currentUser,
                saved.getId(),
                buildTaskDetail(group, saved, attachment != null)
        );
        User publisher = userRepository.findById(currentUser.getUserId()).orElse(null);
        return toTaskDetailVo(saved, group, publisher, attachment == null ? Map.of() : Map.of(attachment.getId(), attachment), null, Map.of());
    }

    @Transactional
    public TaskDetailVo updateTask(Long groupId, Long taskId, UpsertTaskRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        periodService.ensureSelectionOpenForTaskManage();
        RecruitmentTask task = recruitmentTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new NotFoundException("任务不存在"));
        if (!task.getGroupId().equals(groupId)) {
            throw new ConflictException("任务不属于指定分组");
        }
        RecruitmentGroup group = recruitmentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("分组不存在"));
        ensureCanManageGroup(currentUser, group);
        StoredFile oldAttachment = task.getAttachmentFileId() == null ? null : storedFileRepository.findById(task.getAttachmentFileId()).orElse(null);
        StoredFile newAttachment = null;
        if (request.attachmentFileId() != null) {
            newAttachment = fileStorageService.requireOwnedUnboundFile(
                    request.attachmentFileId(),
                    StoredFilePurpose.TASK_ATTACHMENT,
                    currentUser.getUserId()
            );
        }
        boolean willHaveAttachment = newAttachment != null || (!request.removeAttachment() && oldAttachment != null);
        validateTaskPayloadForUpdate(request.contentMarkdown(), willHaveAttachment);

        task.setTitle(request.title().trim());
        task.setContentMarkdown(blankToNull(request.contentMarkdown()));
        task.setMaxScore(request.maxScore());
        task.setDeadlineAt(toLocalDateTime(request.deadlineAt()));
        ensureTaskDeadlineAfterNow(task.getDeadlineAt());

        if (newAttachment != null) {
            task.setAttachmentFileId(newAttachment.getId());
        } else if (request.removeAttachment()) {
            task.setAttachmentFileId(null);
        }

        RecruitmentTask saved = recruitmentTaskRepository.save(task);
        if (newAttachment != null) {
            fileStorageService.bindFile(newAttachment, TASK_BINDING_TYPE, saved.getId());
        }
        if ((newAttachment != null || request.removeAttachment())
                && oldAttachment != null
                && !Objects.equals(oldAttachment.getId(), saved.getAttachmentFileId())) {
            fileStorageService.deleteStoredFile(oldAttachment);
        }
        recordTaskAudit(
                "UPDATE_TASK",
                AuditSeverity.IMPORTANT,
                "更新任务",
                currentUser,
                saved.getId(),
                buildTaskDetail(group, saved, saved.getAttachmentFileId() != null)
        );

        User publisher = userRepository.findById(task.getPublisherUserId()).orElse(null);
        Map<Long, StoredFile> storedFileMap = saved.getAttachmentFileId() == null
                ? Map.of()
                : loadStoredFiles(List.of(saved.getAttachmentFileId()));
        return toTaskDetailVo(saved, group, publisher, storedFileMap, null, Map.of());
    }

    @Transactional
    public void deleteTask(Long groupId, Long taskId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        periodService.ensureSelectionOpenForTaskManage();
        RecruitmentTask task = recruitmentTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new NotFoundException("任务不存在"));
        if (!task.getGroupId().equals(groupId)) {
            throw new ConflictException("任务不属于指定分组");
        }
        ensureCanManageGroup(currentUser, groupId);
        List<TaskSubmission> submissions = taskSubmissionRepository.findAllByTaskId(taskId);
        for (TaskSubmission submission : submissions) {
            StoredFile attachment = submission.getAttachmentFileId() == null
                    ? null
                    : storedFileRepository.findById(submission.getAttachmentFileId()).orElse(null);
            if (attachment != null) {
                submission.setAttachmentFileId(null);
                taskSubmissionRepository.save(submission);
                fileStorageService.deleteStoredFile(attachment);
            }
        }
        taskSubmissionRepository.deleteAll(submissions);

        if (task.getAttachmentFileId() != null) {
            StoredFile taskAttachment = storedFileRepository.findById(task.getAttachmentFileId()).orElse(null);
            task.setAttachmentFileId(null);
            recruitmentTaskRepository.save(task);
            if (taskAttachment != null) {
                fileStorageService.deleteStoredFile(taskAttachment);
            }
        }
        recruitmentTaskRepository.delete(task);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("groupId", groupId);
        detail.put("submissionCount", submissions.size());
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.TASK,
                        "DELETE_TASK",
                        AuditSeverity.IMPORTANT,
                        "删除任务"
                ).actor(currentUser)
                .target("TASK", taskId)
                .detail(detail)
                .build());
    }

    @Transactional(readOnly = true)
    public List<TaskMemberSubmissionVo> listTaskSubmissions(Long taskId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        RecruitmentTask task = recruitmentTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("任务不存在"));
        ensureCanManageGroup(currentUser, task.getGroupId());
        List<GroupMember> groupMembers = groupMemberRepository.findAllByGroupId(task.getGroupId());
        if (groupMembers.isEmpty()) {
            return List.of();
        }
        Map<Long, User> userMap = userRepository.findAllById(
                        groupMembers.stream().map(GroupMember::getUserId).collect(Collectors.toCollection(LinkedHashSet::new))
                ).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Application> applicationMap = applicationRepository.findAllById(
                        groupMembers.stream().map(GroupMember::getApplicationId).toList()
                ).stream()
                .collect(Collectors.toMap(Application::getId, Function.identity()));
        Map<Long, GroupMember> groupMemberByUserId = groupMembers.stream()
                .collect(Collectors.toMap(GroupMember::getUserId, Function.identity()));

        List<TaskSubmission> submissions = taskSubmissionRepository.findAllByTaskId(taskId);
        Map<Long, TaskSubmission> submissionByUserId = submissions.stream()
                .collect(Collectors.toMap(TaskSubmission::getUserId, Function.identity()));
        Set<Long> reviewerIds = submissions.stream()
                .map(TaskSubmission::getReviewerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> reviewerMap = reviewerIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(reviewerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, StoredFile> storedFileMap = loadStoredFileMap(task, submissions);

        return groupMembers.stream()
                .map(groupMember -> {
                    User user = userMap.get(groupMember.getUserId());
                    Application application = applicationMap.get(groupMember.getApplicationId());
                    TaskSubmission submission = submissionByUserId.get(groupMember.getUserId());
                    return new TaskMemberSubmissionVo(
                            groupMember.getUserId(),
                            user == null ? null : user.getUsername(),
                            application == null ? null : application.getRealName(),
                            submission == null ? TaskSubmissionStatus.PENDING : submission.getStatus(),
                            submission == null ? null : submission.getContentMarkdown(),
                            submission == null ? null : toAttachmentVo(findStoredFile(storedFileMap, submission.getAttachmentFileId())),
                            submission == null ? null : toOffsetDateTime(submission.getSubmittedAt()),
                            submission == null ? null : submission.getReviewerUserId(),
                            submission == null || submission.getReviewerUserId() == null
                                    ? null
                                    : Optional.ofNullable(reviewerMap.get(submission.getReviewerUserId())).map(User::getUsername).orElse(null),
                            submission == null ? null : submission.getScore(),
                            submission == null ? null : submission.getReviewComment(),
                            submission == null ? null : toOffsetDateTime(submission.getReviewedAt())
                    );
                })
                .toList();
    }

    @Transactional
    public TaskSubmissionVo reviewTaskSubmission(Long taskId, Long userId, ReviewTaskSubmissionRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        periodService.ensureSelectionOpenForTaskManage();
        RecruitmentTask task = recruitmentTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("任务不存在"));
        ensureCanManageGroup(currentUser, task.getGroupId());
        if (!groupMemberRepository.existsByUserIdAndGroupId(userId, task.getGroupId())) {
            throw new NotFoundException("该用户不在当前任务分组内");
        }
        if (request.score() > task.getMaxScore()) {
            throw new ValidationException("评分不能超过任务满分");
        }

        TaskSubmission submission = taskSubmissionRepository.findByTaskIdAndUserIdForUpdate(taskId, userId)
                .orElseThrow(() -> new ConflictException("该成员尚未提交任务"));
        if (submission.getStatus() == TaskSubmissionStatus.PENDING) {
            throw new ConflictException("该成员尚未提交任务");
        }
        submission.setStatus(TaskSubmissionStatus.REVIEWED);
        submission.setReviewerUserId(currentUser.getUserId());
        submission.setScore(request.score());
        submission.setReviewComment(blankToNull(request.comment()));
        submission.setReviewedAt(LocalDateTime.now(appClock));
        TaskSubmission saved = taskSubmissionRepository.save(submission);
        notificationService.createOrRefresh(new NotificationCommand(
                userId,
                currentUser.getUserId(),
                NotificationType.TASK_REVIEWED,
                "任务已评测",
                buildTaskReviewedContent(task.getTitle(), request.score(), request.comment()),
                "task.reviewed:" + taskId + ":" + userId,
                "TASK",
                taskId
        ));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("taskId", taskId);
        detail.put("userId", userId);
        detail.put("score", request.score());
        detail.put("reviewComment", blankToNull(request.comment()));
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.TASK,
                        "REVIEW_TASK_SUBMISSION",
                        AuditSeverity.IMPORTANT,
                        "评测任务提交"
                ).actor(currentUser)
                .target("TASK", taskId)
                .detail(detail)
                .build());

        Map<Long, StoredFile> storedFileMap = saved.getAttachmentFileId() == null
                ? Map.of()
                : loadStoredFiles(List.of(saved.getAttachmentFileId()));
        Map<Long, User> reviewerMap = userRepository.findAllById(List.of(currentUser.getUserId())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return toTaskSubmissionVo(taskId, userId, saved, storedFileMap, reviewerMap);
    }

    @Transactional
    public void returnTaskSubmission(Long taskId, Long userId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        periodService.ensureSelectionOpenForTaskManage();
        RecruitmentTask task = recruitmentTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("任务不存在"));
        ensureCanManageGroup(currentUser, task.getGroupId());
        if (!groupMemberRepository.existsByUserIdAndGroupId(userId, task.getGroupId())) {
            throw new NotFoundException("该用户不在当前任务分组内");
        }
        TaskSubmission submission = taskSubmissionRepository.findByTaskIdAndUserIdForUpdate(taskId, userId)
                .orElseThrow(() -> new ConflictException("该成员尚未提交任务"));
        if (submission.getStatus() == TaskSubmissionStatus.PENDING) {
            throw new ConflictException("当前提交已经处于待提交状态");
        }
        StoredFile attachment = submission.getAttachmentFileId() == null
                ? null
                : storedFileRepository.findById(submission.getAttachmentFileId()).orElse(null);
        submission.setStatus(TaskSubmissionStatus.PENDING);
        submission.setContentMarkdown(null);
        submission.setAttachmentFileId(null);
        submission.setSubmittedAt(null);
        submission.setReviewerUserId(null);
        submission.setScore(null);
        submission.setReviewComment(null);
        submission.setReviewedAt(null);
        taskSubmissionRepository.save(submission);
        if (attachment != null) {
            fileStorageService.deleteStoredFile(attachment);
        }
        notificationService.createOrRefresh(new NotificationCommand(
                userId,
                currentUser.getUserId(),
                NotificationType.TASK_RETURNED,
                "任务提交已被打回",
                "任务《" + task.getTitle() + "》的提交已被打回，请在截止前重新提交。",
                "task.returned:" + taskId + ":" + userId,
                "TASK",
                taskId
        ));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("taskId", taskId);
        detail.put("userId", userId);
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.TASK,
                        "RETURN_TASK_SUBMISSION",
                        AuditSeverity.IMPORTANT,
                        "打回任务提交"
                ).actor(currentUser)
                .target("TASK", taskId)
                .detail(detail)
                .build());
    }

    @Transactional(readOnly = true)
    public StoredFile getTaskAttachmentFile(Long taskId) {
        RecruitmentTask task = recruitmentTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("任务不存在"));
        if (task.getAttachmentFileId() == null) {
            throw new NotFoundException("当前任务没有附件");
        }
        return storedFileRepository.findById(task.getAttachmentFileId())
                .orElseThrow(() -> new NotFoundException("附件不存在"));
    }

    @Transactional(readOnly = true)
    public StoredFile getCurrentUserSubmissionAttachmentFile(Long taskId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        TaskSubmission submission = taskSubmissionRepository.findByTaskIdAndUserId(taskId, currentUser.getUserId())
                .orElseThrow(() -> new NotFoundException("当前任务提交不存在"));
        if (submission.getAttachmentFileId() == null) {
            throw new NotFoundException("当前任务提交没有附件");
        }
        return storedFileRepository.findById(submission.getAttachmentFileId())
                .orElseThrow(() -> new NotFoundException("附件不存在"));
    }

    @Transactional(readOnly = true)
    public StoredFile getMemberSubmissionAttachmentFile(Long taskId, Long userId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        RecruitmentTask task = recruitmentTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("任务不存在"));
        ensureCanManageGroup(currentUser, task.getGroupId());
        if (!groupMemberRepository.existsByUserIdAndGroupId(userId, task.getGroupId())) {
            throw new ForbiddenException("该用户当前不属于任务所在分组");
        }
        TaskSubmission submission = taskSubmissionRepository.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new NotFoundException("该成员任务提交不存在"));
        if (submission.getAttachmentFileId() == null) {
            throw new NotFoundException("该成员任务提交没有附件");
        }
        return storedFileRepository.findById(submission.getAttachmentFileId())
                .orElseThrow(() -> new NotFoundException("附件不存在"));
    }

    private TaskDetailVo toTaskDetailVo(
            RecruitmentTask task,
            RecruitmentGroup group,
            User publisher,
            Map<Long, StoredFile> storedFileMap,
            TaskSubmission submission,
            Map<Long, User> reviewerMap
    ) {
        return new TaskDetailVo(
                task.getId(),
                task.getGroupId(),
                group.getName(),
                task.getTitle(),
                task.getContentMarkdown(),
                toAttachmentVo(findStoredFile(storedFileMap, task.getAttachmentFileId())),
                task.getMaxScore(),
                toOffsetDateTime(task.getDeadlineAt()),
                task.getPublisherUserId(),
                publisher == null ? null : publisher.getUsername(),
                toOffsetDateTime(task.getCreatedAt()),
                toOffsetDateTime(task.getUpdatedAt()),
                submission == null ? null : toTaskSubmissionVo(task.getId(), submission.getUserId(), submission, storedFileMap, reviewerMap)
        );
    }

    private TaskSubmissionVo toTaskSubmissionVo(
            Long taskId,
            Long userId,
            TaskSubmission submission,
            Map<Long, StoredFile> storedFileMap,
            Map<Long, User> reviewerMap
    ) {
        if (submission == null) {
            return new TaskSubmissionVo(taskId, userId, TaskSubmissionStatus.PENDING, null, null, null, null, null, null, null, null);
        }
        User reviewer = submission.getReviewerUserId() == null ? null : reviewerMap.get(submission.getReviewerUserId());
        return new TaskSubmissionVo(
                taskId,
                userId,
                submission.getStatus(),
                submission.getContentMarkdown(),
                toAttachmentVo(findStoredFile(storedFileMap, submission.getAttachmentFileId())),
                toOffsetDateTime(submission.getSubmittedAt()),
                submission.getReviewerUserId(),
                reviewer == null ? null : reviewer.getUsername(),
                submission.getScore(),
                submission.getReviewComment(),
                toOffsetDateTime(submission.getReviewedAt())
        );
    }

    private Map<Long, StoredFile> loadStoredFileMap(RecruitmentTask task, List<TaskSubmission> submissions) {
        Set<Long> fileIds = new LinkedHashSet<>();
        if (task.getAttachmentFileId() != null) {
            fileIds.add(task.getAttachmentFileId());
        }
        for (TaskSubmission submission : submissions) {
            if (submission != null && submission.getAttachmentFileId() != null) {
                fileIds.add(submission.getAttachmentFileId());
            }
        }
        return loadStoredFiles(fileIds);
    }

    private Map<Long, StoredFile> loadStoredFiles(Collection<Long> fileIds) {
        List<Long> ids = fileIds.stream().filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return storedFileRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(StoredFile::getId, Function.identity()));
    }

    private StoredFile findStoredFile(Map<Long, StoredFile> storedFileMap, Long fileId) {
        if (fileId == null || storedFileMap == null || storedFileMap.isEmpty()) {
            return null;
        }
        return storedFileMap.get(fileId);
    }

    private TaskAttachmentVo toAttachmentVo(StoredFile storedFile) {
        if (storedFile == null) {
            return null;
        }
        return new TaskAttachmentVo(
                storedFile.getId(),
                storedFile.getOriginalFileName(),
                storedFile.getContentType(),
                storedFile.getSizeBytes()
        );
    }

    private void validateTaskPayload(String contentMarkdown, Long attachmentFileId) {
        if (isBlank(contentMarkdown) && attachmentFileId == null) {
            throw new ValidationException("任务内容和任务附件不能同时为空");
        }
    }

    private void validateTaskPayloadForUpdate(String contentMarkdown, boolean willHaveAttachment) {
        if (isBlank(contentMarkdown) && !willHaveAttachment) {
            throw new ValidationException("任务内容和任务附件不能同时为空");
        }
    }

    private void validateSubmitPayload(String contentMarkdown, Long attachmentFileId) {
        if (isBlank(contentMarkdown) && attachmentFileId == null) {
            throw new ValidationException("提交内容和提交附件不能同时为空");
        }
    }

    private void ensureTaskDeadlineAfterNow(LocalDateTime deadlineAt) {
        if (!deadlineAt.isAfter(LocalDateTime.now(appClock))) {
            throw new ValidationException("任务截止时间必须晚于当前时间");
        }
    }

    private void ensureTaskDeadlineNotPassed(RecruitmentTask task) {
        if (LocalDateTime.now(appClock).isAfter(task.getDeadlineAt())) {
            throw new ConflictException("当前任务已截止，不能再提交");
        }
    }

    private void ensureCanManageGroup(LoginUser currentUser, Long groupId) {
        RecruitmentGroup group = recruitmentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("分组不存在"));
        ensureCanManageGroup(currentUser, group);
    }

    private void ensureCanManageGroup(LoginUser currentUser, RecruitmentGroup group) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() != Role.LEADER || !Objects.equals(group.getLeaderUserId(), currentUser.getUserId())) {
            throw new ForbiddenException("无权操作该分组任务");
        }
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return value.atZoneSameInstant(appClock.getZone()).toLocalDateTime();
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(appClock.getZone()).toOffsetDateTime();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void notifyTaskPublished(LoginUser actor, RecruitmentGroup group, RecruitmentTask task) {
        List<NotificationCommand> commands = groupMemberRepository.findAllByGroupId(group.getId()).stream()
                .map(GroupMember::getUserId)
                .filter(userId -> !Objects.equals(userId, actor.getUserId()))
                .distinct()
                .map(userId -> new NotificationCommand(
                        userId,
                        actor.getUserId(),
                        NotificationType.TASK_PUBLISHED,
                        "分组任务已发布",
                        "分组 " + group.getName() + " 发布了新任务：《" + task.getTitle() + "》",
                        "task.published:" + task.getId(),
                        "TASK",
                        task.getId()
                ))
                .toList();
        notificationService.createOrRefreshAll(commands);
    }

    private Map<String, Object> buildTaskDetail(RecruitmentGroup group, RecruitmentTask task, boolean hasAttachment) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("groupId", group.getId());
        detail.put("groupName", group.getName());
        detail.put("title", task.getTitle());
        detail.put("maxScore", task.getMaxScore());
        detail.put("deadlineAt", task.getDeadlineAt());
        detail.put("hasAttachment", hasAttachment);
        return detail;
    }

    private void recordTaskAudit(
            String action,
            AuditSeverity severity,
            String summary,
            LoginUser actor,
            Long taskId,
            Map<String, Object> detail
    ) {
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.TASK,
                        action,
                        severity,
                        summary
                ).actor(actor)
                .target("TASK", taskId)
                .detail(detail)
                .build());
    }

    private String buildTaskReviewedContent(String taskTitle, Integer score, String comment) {
        String normalizedComment = blankToNull(comment);
        if (normalizedComment == null) {
            return "任务《" + taskTitle + "》已完成评测，得分：" + score;
        }
        return "任务《" + taskTitle + "》已完成评测，得分：" + score + "，评语：" + normalizedComment;
    }
}
