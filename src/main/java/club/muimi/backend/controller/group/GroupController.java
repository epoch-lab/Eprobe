package club.muimi.backend.controller.group;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.service.group.GroupManagementService;
import club.muimi.backend.vo.group.GroupDetailVo;
import club.muimi.backend.vo.group.GroupMemberVo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupManagementService groupManagementService;

    public GroupController(GroupManagementService groupManagementService) {
        this.groupManagementService = groupManagementService;
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("@authzService.canViewGroup(authentication, #groupId)")
    public ApiResponse<GroupDetailVo> getGroupDetail(@PathVariable Long groupId) {
        return ApiResponse.success(groupManagementService.getGroupDetail(groupId), "ok");
    }

    @GetMapping("/{groupId}/members")
    @PreAuthorize("hasAnyRole('LEADER','ADMIN') and @authzService.canManageGroup(authentication, #groupId)")
    public ApiResponse<List<GroupMemberVo>> listGroupMembers(@PathVariable Long groupId) {
        return ApiResponse.success(groupManagementService.listGroupMembers(groupId), "ok");
    }
}
