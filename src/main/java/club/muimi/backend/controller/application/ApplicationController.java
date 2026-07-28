package club.muimi.backend.controller.application;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.dto.application.UpsertApplicationRequest;
import club.muimi.backend.service.application.ApplicationService;
import club.muimi.backend.vo.application.ApplicationDetailVo;
import club.muimi.backend.vo.application.ApplicationSummaryVo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ApiResponse<List<ApplicationDetailVo>> listApplications() {
        return ApiResponse.success(applicationService.listCurrentUserApplications(), "ok");
    }

    @GetMapping("/summary")
    public ApiResponse<ApplicationSummaryVo> getSummary() {
        return ApiResponse.success(applicationService.getCurrentUserSummary(), "ok");
    }

    @GetMapping("/{applicationId}")
    public ApiResponse<ApplicationDetailVo> getApplication(@PathVariable Long applicationId) {
        return ApiResponse.success(applicationService.getCurrentUserApplication(applicationId), "ok");
    }

    @PostMapping
    public ApiResponse<ApplicationDetailVo> createApplication(@Valid @RequestBody UpsertApplicationRequest request) {
        return ApiResponse.success(applicationService.createApplication(request), "报名申请提交成功");
    }

    @PutMapping("/{applicationId}")
    public ApiResponse<ApplicationDetailVo> updateApplication(
            @PathVariable Long applicationId,
            @Valid @RequestBody UpsertApplicationRequest request
    ) {
        return ApiResponse.success(applicationService.updateApplication(applicationId, request), "报名申请更新成功");
    }

    @DeleteMapping("/{applicationId}")
    public ApiResponse<Void> withdrawApplication(@PathVariable Long applicationId) {
        applicationService.withdrawApplication(applicationId);
        return ApiResponse.success(null, "报名申请撤回成功");
    }
}
