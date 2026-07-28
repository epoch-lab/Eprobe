package club.muimi.backend.controller.task;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.controller.support.FileDownloadResponseBuilder;
import club.muimi.backend.dto.task.SubmitTaskRequest;
import club.muimi.backend.service.task.TaskService;
import club.muimi.backend.vo.task.TaskDetailVo;
import club.muimi.backend.vo.task.TaskSubmissionVo;
import club.muimi.backend.vo.task.TaskSummaryVo;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;
    private final FileDownloadResponseBuilder fileDownloadResponseBuilder;

    public TaskController(TaskService taskService, FileDownloadResponseBuilder fileDownloadResponseBuilder) {
        this.taskService = taskService;
        this.fileDownloadResponseBuilder = fileDownloadResponseBuilder;
    }

    @GetMapping
    public ApiResponse<List<TaskSummaryVo>> listCurrentUserTasks() {
        return ApiResponse.success(taskService.listCurrentUserTasks(), "ok");
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("@authzService.canAccessTask(authentication, #taskId)")
    public ApiResponse<TaskDetailVo> getTaskDetail(@PathVariable Long taskId) {
        return ApiResponse.success(taskService.getTaskDetail(taskId), "ok");
    }

    @GetMapping("/{taskId}/submission")
    @PreAuthorize("@authzService.canAccessTask(authentication, #taskId)")
    public ApiResponse<TaskSubmissionVo> getCurrentUserSubmission(@PathVariable Long taskId) {
        return ApiResponse.success(taskService.getCurrentUserSubmission(taskId), "ok");
    }

    @PostMapping("/{taskId}/submission")
    @PreAuthorize("@authzService.canAccessTask(authentication, #taskId)")
    public ApiResponse<TaskSubmissionVo> submitTask(
            @PathVariable Long taskId,
            @Valid @RequestBody SubmitTaskRequest request
    ) {
        return ApiResponse.success(taskService.submitTask(taskId, request), "任务提交成功");
    }

    @GetMapping("/{taskId}/attachment")
    @PreAuthorize("@authzService.canAccessTask(authentication, #taskId)")
    public ResponseEntity<Resource> downloadTaskAttachment(@PathVariable Long taskId) {
        return fileDownloadResponseBuilder.build(taskService.getTaskAttachmentFile(taskId));
    }

    @GetMapping("/{taskId}/submission/attachment")
    @PreAuthorize("@authzService.canAccessTask(authentication, #taskId)")
    public ResponseEntity<Resource> downloadCurrentUserSubmissionAttachment(@PathVariable Long taskId) {
        return fileDownloadResponseBuilder.build(taskService.getCurrentUserSubmissionAttachmentFile(taskId));
    }
}
