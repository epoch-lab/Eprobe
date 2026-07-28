package club.muimi.backend.controller.admin;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.common.api.PageResult;
import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.dto.admin.UpdateUserRoleRequest;
import club.muimi.backend.dto.admin.UpdateUserStatusRequest;
import club.muimi.backend.service.admin.AdminUserService;
import club.muimi.backend.vo.admin.AdminUserDetailVo;
import club.muimi.backend.vo.admin.AdminUserSummaryVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminUserSummaryVo>> listUsers(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码必须大于等于 1")
            int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页条数必须大于等于 1")
            @Max(value = 50, message = "每页条数不能超过 50")
            int size,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(adminUserService.listUsers(page, size, role, status, keyword), "ok");
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminUserDetailVo> getUserDetail(@PathVariable Long userId) {
        return ApiResponse.success(adminUserService.getUserDetail(userId), "ok");
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<AdminUserDetailVo> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        return ApiResponse.success(adminUserService.updateUserStatus(userId, request), "用户状态更新成功");
    }

    @PatchMapping("/{userId}/role")
    public ApiResponse<AdminUserDetailVo> updateUserRole(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        return ApiResponse.success(adminUserService.updateUserRole(userId, request), "用户角色更新成功");
    }
}
