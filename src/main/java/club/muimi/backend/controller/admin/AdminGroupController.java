package club.muimi.backend.controller.admin;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.dto.admin.AssignGroupLeaderRequest;
import club.muimi.backend.dto.admin.GroupUpsertRequest;
import club.muimi.backend.dto.admin.UnassignGroupApplicationRequest;
import club.muimi.backend.service.application.ApplicationReviewService;
import club.muimi.backend.service.group.GroupAdminService;
import club.muimi.backend.service.group.GroupManagementService;
import club.muimi.backend.vo.group.GroupDetailVo;
import club.muimi.backend.vo.group.ManageableGroupVo;
import club.muimi.backend.vo.group.UngroupedApplicationVo;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/groups")
@PreAuthorize("hasRole('ADMIN')")
public class AdminGroupController {

    private final GroupManagementService groupManagementService;
    private final GroupAdminService groupAdminService;
    private final ApplicationReviewService applicationReviewService;

    public AdminGroupController(
            GroupManagementService groupManagementService,
            GroupAdminService groupAdminService,
            ApplicationReviewService applicationReviewService
    ) {
        this.groupManagementService = groupManagementService;
        this.groupAdminService = groupAdminService;
        this.applicationReviewService = applicationReviewService;
    }

    @GetMapping
    public ApiResponse<List<ManageableGroupVo>> listGroups() {
        return ApiResponse.success(groupAdminService.listAllGroups(), "ok");
    }

    @GetMapping("/{groupId}")
    public ApiResponse<GroupDetailVo> getGroup(@PathVariable Long groupId) {
        return ApiResponse.success(groupAdminService.getGroupDetail(groupId), "ok");
    }

    @PostMapping
    public ApiResponse<GroupDetailVo> createGroup(@Valid @RequestBody GroupUpsertRequest request) {
        return ApiResponse.success(groupAdminService.createGroup(request), "分组创建成功");
    }

    @PutMapping("/{groupId}")
    public ApiResponse<GroupDetailVo> updateGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody GroupUpsertRequest request
    ) {
        return ApiResponse.success(groupAdminService.updateGroup(groupId, request), "分组更新成功");
    }

    @DeleteMapping("/{groupId}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long groupId) {
        groupAdminService.deleteGroup(groupId);
        return ApiResponse.success(null, "分组删除成功");
    }

    @PutMapping("/{groupId}/leader")
    public ApiResponse<GroupDetailVo> assignLeader(
            @PathVariable Long groupId,
            @Valid @RequestBody AssignGroupLeaderRequest request
    ) {
        return ApiResponse.success(groupAdminService.assignLeader(groupId, request), "分组负责人指派成功");
    }

    @DeleteMapping("/{groupId}/leader")
    public ApiResponse<GroupDetailVo> removeLeader(@PathVariable Long groupId) {
        return ApiResponse.success(groupAdminService.removeLeader(groupId), "分组负责人移除成功");
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
