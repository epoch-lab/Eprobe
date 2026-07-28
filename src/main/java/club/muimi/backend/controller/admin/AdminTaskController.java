package club.muimi.backend.controller.admin;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.controller.support.FileDownloadResponseBuilder;
import club.muimi.backend.dto.task.ReviewTaskSubmissionRequest;
import club.muimi.backend.dto.task.UpsertTaskRequest;
import club.muimi.backend.service.task.TaskService;
import club.muimi.backend.vo.task.ManageTaskVo;
import club.muimi.backend.vo.task.TaskDetailVo;
import club.muimi.backend.vo.task.TaskMemberSubmissionVo;
import club.muimi.backend.vo.task.TaskSubmissionVo;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AdminTaskController {

    private final TaskService taskService;
    private final FileDownloadResponseBuilder fileDownloadResponseBuilder;

    public AdminTaskController(TaskService taskService, FileDownloadResponseBuilder fileDownloadResponseBuilder) {
        this.taskService = taskService;
        this.fileDownloadResponseBuilder = fileDownloadResponseBuilder;
    }

    @GetMapping("/api/v1/admin/groups/{groupId}/tasks")
    @PreAuthorize("@authzService.canManageGroup(authentication, #groupId)")
    public ApiResponse<List<ManageTaskVo>> listManageableTasks(@PathVariable Long groupId) {
        return ApiResponse.success(taskService.listManageableTasks(groupId), "ok");
    }

    @PostMapping("/api/v1/admin/groups/{groupId}/tasks")
    @PreAuthorize("@authzService.canManageGroup(authentication, #groupId)")
    public ApiResponse<TaskDetailVo> createTask(
            @PathVariable Long groupId,
            @Valid @RequestBody UpsertTaskRequest request
    ) {
        return ApiResponse.success(taskService.createTask(groupId, request), "任务创建成功");
    }

    @PutMapping("/api/v1/admin/groups/{groupId}/tasks/{taskId}")
    @PreAuthorize("@authzService.canManageGroup(authentication, #groupId)")
    public ApiResponse<TaskDetailVo> updateTask(
            @PathVariable Long groupId,
            @PathVariable Long taskId,
            @Valid @RequestBody UpsertTaskRequest request
    ) {
        return ApiResponse.success(taskService.updateTask(groupId, taskId, request), "任务更新成功");
    }

    @DeleteMapping("/api/v1/admin/groups/{groupId}/tasks/{taskId}")
    @PreAuthorize("@authzService.canManageGroup(authentication, #groupId)")
    public ApiResponse<Void> deleteTask(@PathVariable Long groupId, @PathVariable Long taskId) {
        taskService.deleteTask(groupId, taskId);
        return ApiResponse.success(null, "任务删除成功");
    }

    @GetMapping("/api/v1/admin/tasks/{taskId}/submissions")
    @PreAuthorize("@authzService.canManageTask(authentication, #taskId)")
    public ApiResponse<List<TaskMemberSubmissionVo>> listTaskSubmissions(@PathVariable Long taskId) {
        return ApiResponse.success(taskService.listTaskSubmissions(taskId), "ok");
    }

    @PostMapping("/api/v1/admin/tasks/{taskId}/submissions/{userId}/review")
    @PreAuthorize("@authzService.canManageTask(authentication, #taskId)")
    public ApiResponse<TaskSubmissionVo> reviewTaskSubmission(
            @PathVariable Long taskId,
            @PathVariable Long userId,
            @Valid @RequestBody ReviewTaskSubmissionRequest request
    ) {
        return ApiResponse.success(taskService.reviewTaskSubmission(taskId, userId, request), "任务评测成功");
    }

    @PostMapping("/api/v1/admin/tasks/{taskId}/submissions/{userId}/return")
    @PreAuthorize("@authzService.canManageTask(authentication, #taskId)")
    public ApiResponse<Void> returnTaskSubmission(@PathVariable Long taskId, @PathVariable Long userId) {
        taskService.returnTaskSubmission(taskId, userId);
        return ApiResponse.success(null, "任务已打回，成员可重新提交");
    }

    @GetMapping("/api/v1/admin/tasks/{taskId}/submissions/{userId}/attachment")
    @PreAuthorize("@authzService.canManageTask(authentication, #taskId)")
    public ResponseEntity<Resource> downloadMemberSubmissionAttachment(
            @PathVariable Long taskId,
            @PathVariable Long userId
    ) {
        return fileDownloadResponseBuilder.build(taskService.getMemberSubmissionAttachmentFile(taskId, userId));
    }
}
