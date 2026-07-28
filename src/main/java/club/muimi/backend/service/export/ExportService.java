package club.muimi.backend.service.export;

import club.muimi.backend.common.enums.AuditModule;
import club.muimi.backend.common.enums.AuditSeverity;
import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.TaskSubmissionStatus;
import club.muimi.backend.entity.*;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.exception.NotFoundException;
import club.muimi.backend.repository.*;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogCommand;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.user.CurrentUserService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private final CurrentUserService currentUserService;
    private final ApplicationRepository applicationRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final RecruitmentGroupRepository recruitmentGroupRepository;
    private final RecruitmentTaskRepository recruitmentTaskRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final UserRepository userRepository;
    private final DirectionRepository directionRepository;
    private final StoredFileRepository storedFileRepository;
    private final AuditLogService auditLogService;
    private final Clock appClock;

    public ExportService(
            CurrentUserService currentUserService,
            ApplicationRepository applicationRepository,
            GroupMemberRepository groupMemberRepository,
            RecruitmentGroupRepository recruitmentGroupRepository,
            RecruitmentTaskRepository recruitmentTaskRepository,
            TaskSubmissionRepository taskSubmissionRepository,
            UserRepository userRepository,
            DirectionRepository directionRepository,
            StoredFileRepository storedFileRepository,
            AuditLogService auditLogService,
            Clock appClock
    ) {
        this.currentUserService = currentUserService;
        this.applicationRepository = applicationRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.recruitmentGroupRepository = recruitmentGroupRepository;
        this.recruitmentTaskRepository = recruitmentTaskRepository;
        this.taskSubmissionRepository = taskSubmissionRepository;
        this.userRepository = userRepository;
        this.directionRepository = directionRepository;
        this.storedFileRepository = storedFileRepository;
        this.auditLogService = auditLogService;
        this.appClock = appClock;
    }

    @Transactional(readOnly = true)
    public byte[] exportApplications() {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new ForbiddenException("只有管理员可以导出报名信息");
        }
        List<Application> applications = applicationRepository.findAll();
        List<Long> applicationIds = applications.stream().map(Application::getId).toList();
        List<GroupMember> groupMembers = applicationIds.isEmpty()
                ? List.of()
                : groupMemberRepository.findAllByApplicationIdIn(applicationIds);
        Map<Long, GroupMember> groupMemberMap = groupMembers.stream().collect(Collectors.toMap(GroupMember::getApplicationId, Function.identity()));
        Set<Long> groupIds = groupMembers.stream().map(GroupMember::getGroupId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, RecruitmentGroup> groupMap = groupIds.isEmpty()
                ? Map.of()
                : recruitmentGroupRepository.findAllByIdIn(groupIds).stream()
                .collect(Collectors.toMap(RecruitmentGroup::getId, Function.identity()));
        Set<Long> userIds = applications.stream().map(Application::getUserId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<Long> directionIds = applications.stream()
                .flatMap(application -> java.util.stream.Stream.of(application.getDirectionLevel1Id(), application.getDirectionLevel2Id()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Direction> directionMap = directionIds.isEmpty()
                ? Map.of()
                : directionRepository.findAllById(directionIds).stream()
                .collect(Collectors.toMap(Direction::getId, Function.identity()));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("applications");
            writeRow(sheet, 0, "申请ID", "用户ID", "用户名", "邮箱", "真实姓名", "手机号", "学院", "专业", "班级", "年级", "入学年份",
                    "一级方向", "二级方向", "状态", "状态备注", "分组ID", "分组名称", "创建时间", "更新时间");
            int rowIndex = 1;
            for (Application application : applications) {
                GroupMember groupMember = groupMemberMap.get(application.getId());
                RecruitmentGroup group = groupMember == null ? null : groupMap.get(groupMember.getGroupId());
                User user = userMap.get(application.getUserId());
                Direction level1 = directionMap.get(application.getDirectionLevel1Id());
                Direction level2 = directionMap.get(application.getDirectionLevel2Id());
                writeRow(sheet, rowIndex++,
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
                        level1 == null ? null : level1.getName(),
                        level2 == null ? null : level2.getName(),
                        application.getStatus(),
                        application.getStatusRemark(),
                        group == null ? null : group.getId(),
                        group == null ? null : group.getName(),
                        application.getCreatedAt().atZone(appClock.getZone()).toOffsetDateTime(),
                        application.getUpdatedAt().atZone(appClock.getZone()).toOffsetDateTime()
                );
            }
            byte[] bytes = toBytes(workbook);
            recordExportAudit(currentUser, "EXPORT_APPLICATIONS", "导出报名信息", null, bytes.length);
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("导出报名信息失败", exception);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportAdminGroupMembers() {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new ForbiddenException("只有管理员可以导出分组结果");
        }
        return exportGroupMembersInternal(
                currentUser,
                "EXPORT_ALL_GROUP_MEMBERS",
                "导出全部分组成员",
                recruitmentGroupRepository.findAllByOrderByCreatedAtDesc().stream().map(RecruitmentGroup::getId).toList(),
                null
        );
    }

    @Transactional(readOnly = true)
    public byte[] exportManageableGroupMembers(Long groupId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        RecruitmentGroup group = recruitmentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("分组不存在"));
        if (currentUser.getRole() != Role.ADMIN && !Objects.equals(group.getLeaderUserId(), currentUser.getUserId())) {
            throw new ForbiddenException("当前用户无权导出该分组成员结果");
        }
        return exportGroupMembersInternal(currentUser, "EXPORT_GROUP_MEMBERS", "导出分组成员", List.of(groupId), groupId);
    }

    @Transactional(readOnly = true)
    public byte[] exportManageableGroupTaskResults(Long groupId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        RecruitmentGroup group = recruitmentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("分组不存在"));
        if (currentUser.getRole() != Role.ADMIN && !Objects.equals(group.getLeaderUserId(), currentUser.getUserId())) {
            throw new ForbiddenException("当前用户无权导出该分组任务结果");
        }
        List<RecruitmentTask> tasks = recruitmentTaskRepository.findAllByGroupIdOrderByCreatedAtDesc(groupId);
        List<Long> taskIds = tasks.stream().map(RecruitmentTask::getId).toList();
        List<TaskSubmission> submissions = taskIds.isEmpty()
                ? List.of()
                : taskSubmissionRepository.findAllByTaskIdIn(taskIds);
        Map<TaskSubmissionKey, TaskSubmission> submissionMap = submissions.stream()
                .collect(Collectors.toMap(
                        submission -> new TaskSubmissionKey(submission.getTaskId(), submission.getUserId()),
                        Function.identity(),
                        (left, right) -> left
                ));
        List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
        Map<Long, User> userMap = userRepository.findAllById(
                        members.stream().map(GroupMember::getUserId).collect(Collectors.toCollection(LinkedHashSet::new))
                ).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Application> applicationMap = applicationRepository.findAllById(
                        members.stream().map(GroupMember::getApplicationId).toList()
                ).stream()
                .collect(Collectors.toMap(Application::getId, Function.identity()));
        Map<Long, User> reviewerMap = userRepository.findAllById(
                        submissions.stream().map(TaskSubmission::getReviewerUserId).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new))
                ).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("group-task-results");
            writeRow(sheet, 0, "分组ID", "分组名称", "任务ID", "任务标题", "成员ID", "用户名", "真实姓名", "提交状态",
                    "提交时间", "评测时间", "评测人", "分数", "评语");
            int rowIndex = 1;
            for (RecruitmentTask task : tasks) {
                for (GroupMember member : members) {
                    TaskSubmission submission = submissionMap.get(new TaskSubmissionKey(task.getId(), member.getUserId()));
                    User user = userMap.get(member.getUserId());
                    Application application = applicationMap.get(member.getApplicationId());
                    User reviewer = submission == null || submission.getReviewerUserId() == null ? null : reviewerMap.get(submission.getReviewerUserId());
                    writeRow(sheet, rowIndex++,
                            groupId,
                            group.getName(),
                            task.getId(),
                            task.getTitle(),
                            member.getUserId(),
                            user == null ? null : user.getUsername(),
                            application == null ? null : application.getRealName(),
                            submission == null ? TaskSubmissionStatus.PENDING : submission.getStatus(),
                            submission == null || submission.getSubmittedAt() == null ? null : submission.getSubmittedAt().atZone(appClock.getZone()).toOffsetDateTime(),
                            submission == null || submission.getReviewedAt() == null ? null : submission.getReviewedAt().atZone(appClock.getZone()).toOffsetDateTime(),
                            reviewer == null ? null : reviewer.getUsername(),
                            submission == null ? null : submission.getScore(),
                            submission == null ? null : submission.getReviewComment()
                    );
                }
            }
            byte[] bytes = toBytes(workbook);
            recordExportAudit(currentUser, "EXPORT_GROUP_TASK_RESULTS", "导出分组任务结果", groupId, bytes.length);
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("导出任务结果失败", exception);
        }
    }

    private byte[] exportGroupMembersInternal(
            LoginUser currentUser,
            String action,
            String summary,
            List<Long> groupIds,
            Long groupId
    ) {
        List<RecruitmentGroup> groups = groupIds.isEmpty() ? List.of() : recruitmentGroupRepository.findAllByIdIn(groupIds);
        Map<Long, RecruitmentGroup> groupMap = groups.stream().collect(Collectors.toMap(RecruitmentGroup::getId, Function.identity()));
        List<GroupMember> members = groupIds.isEmpty() ? List.of() : groupMemberRepository.findAllByGroupIdIn(groupIds);
        Set<Long> userIds = members.stream().map(GroupMember::getUserId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<Long> applicationIds = members.stream().map(GroupMember::getApplicationId).toList();
        Map<Long, Application> applicationMap = applicationIds.isEmpty()
                ? Map.of()
                : applicationRepository.findAllById(applicationIds).stream()
                .collect(Collectors.toMap(Application::getId, Function.identity()));
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("group-members");
            writeRow(sheet, 0, "分组ID", "分组名称", "用户ID", "用户名", "邮箱", "申请ID", "真实姓名", "年级", "入学年份", "加入时间");
            int rowIndex = 1;
            for (GroupMember member : members) {
                RecruitmentGroup group = groupMap.get(member.getGroupId());
                User user = userMap.get(member.getUserId());
                Application application = applicationMap.get(member.getApplicationId());
                writeRow(sheet, rowIndex++,
                        member.getGroupId(),
                        group == null ? null : group.getName(),
                        member.getUserId(),
                        user == null ? null : user.getUsername(),
                        user == null ? null : user.getEmail(),
                        member.getApplicationId(),
                        application == null ? null : application.getRealName(),
                        application == null ? null : application.getGrade(),
                        application == null ? null : application.getAdmissionYear(),
                        member.getJoinedAt().atZone(appClock.getZone()).toOffsetDateTime()
                );
            }
            byte[] bytes = toBytes(workbook);
            recordExportAudit(currentUser, action, summary, groupId, bytes.length);
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("导出分组结果失败", exception);
        }
    }

    private void writeRow(XSSFSheet sheet, int rowIndex, Object... values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i] == null ? "" : String.valueOf(values[i]));
        }
    }

    private byte[] toBytes(XSSFWorkbook workbook) throws IOException {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            XSSFSheet sheet = workbook.getSheetAt(i);
            if (sheet.getRow(0) != null) {
                for (int cellIndex = 0; cellIndex < sheet.getRow(0).getLastCellNum(); cellIndex++) {
                    sheet.autoSizeColumn(cellIndex);
                }
            }
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void recordExportAudit(LoginUser actor, String action, String summary, Long groupId, int fileSizeBytes) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("groupId", groupId);
        detail.put("fileSizeBytes", fileSizeBytes);
        auditLogService.recordInNewTransaction(AuditLogCommand.builder(
                        AuditModule.EXPORT,
                        action,
                        AuditSeverity.IMPORTANT,
                        summary
                ).actor(actor)
                .target("EXPORT", groupId)
                .detail(detail)
                .build());
    }

    private record TaskSubmissionKey(Long taskId, Long userId) {
    }
}
