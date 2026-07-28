package club.muimi.backend.controller.leader;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.dto.admin.RejectApplicationRequest;
import club.muimi.backend.service.application.ApplicationReviewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/leader/applications")
@PreAuthorize("hasAnyRole('LEADER','ADMIN')")
public class LeaderApplicationReviewController {

    private final ApplicationReviewService applicationReviewService;

    public LeaderApplicationReviewController(ApplicationReviewService applicationReviewService) {
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
