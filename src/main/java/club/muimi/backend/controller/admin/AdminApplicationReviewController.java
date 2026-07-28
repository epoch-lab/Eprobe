package club.muimi.backend.controller.admin;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.dto.admin.RejectApplicationRequest;
import club.muimi.backend.service.application.ApplicationReviewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/applications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApplicationReviewController {

    private final ApplicationReviewService applicationReviewService;

    public AdminApplicationReviewController(ApplicationReviewService applicationReviewService) {
        this.applicationReviewService = applicationReviewService;
    }

    @PostMapping("/{applicationId}/reject")
    public ApiResponse<Void> rejectApplication(
            @PathVariable Long applicationId,
            @Valid @RequestBody RejectApplicationRequest request
    ) {
        applicationReviewService.rejectApplication(applicationId, request);
        return ApiResponse.success(null, "报名申请已拒绝");
    }
}
