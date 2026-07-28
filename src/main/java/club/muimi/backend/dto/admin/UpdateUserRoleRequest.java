package club.muimi.backend.dto.admin;

import club.muimi.backend.common.enums.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "用户角色不能为空")
        Role role
) {
}
