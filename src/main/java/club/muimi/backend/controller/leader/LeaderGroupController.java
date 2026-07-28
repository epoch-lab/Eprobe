package club.muimi.backend.controller.leader;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.dto.admin.UnassignGroupApplicationRequest;
import club.muimi.backend.service.application.ApplicationReviewService;
import club.muimi.backend.service.group.GroupManagementService;
import club.muimi.backend.vo.group.ManageableGroupVo;
import club.muimi.backend.vo.group.UngroupedApplicationVo;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leader/groups")
@PreAuthorize("hasAnyRole('LEADER','ADMIN')")
public class LeaderGroupController {

    private final GroupManagementService groupManagementService;
    private final ApplicationReviewService applicationReviewService;

    public LeaderGroupController(
            GroupManagementService groupManagementService,
            ApplicationReviewService applicationReviewService
    ) {
        this.groupManagementService = groupManagementService;
        this.applicationReviewService = applicationReviewService;
    }

    @GetMapping
    public ApiResponse<List<ManageableGroupVo>> listGroups() {
        return ApiResponse.success(groupManagementService.listManageableGroups(), "ok");
    }

    @GetMapping("/ungrouped-applications")
    public ApiResponse<List<UngroupedApplicationVo>> listUngroupedApplications() {
        return ApiResponse.success(groupManagementService.listUngroupedApplications(), "ok");
    }

    @PostMapping("/{groupId}/applications/{applicationId}")
    public ApiResponse<Void> assignApplicationToGroup(
            @PathVariable Long groupId,
            @PathVariable Long applicationId
    ) {
        groupManagementService.assignApplicationToGroup(groupId, applicationId);
        return ApiResponse.success(null, "分组成功");
    }

    @PostMapping("/{groupId}/applications/{applicationId}/unassign")
    public ApiResponse<Void> unassignApplicationFromGroup(
            @PathVariable Long groupId,
            @PathVariable Long applicationId,
            @Valid @RequestBody(required = false) UnassignGroupApplicationRequest request
    ) {
        applicationReviewService.unassignApplicationFromGroup(
                groupId,
                applicationId,
                request == null ? new UnassignGroupApplicationRequest(null) : request
        );
        return ApiResponse.success(null, "取消分组成功");
    }
}
