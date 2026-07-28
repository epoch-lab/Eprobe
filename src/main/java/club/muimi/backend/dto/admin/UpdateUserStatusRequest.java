package club.muimi.backend.dto.admin;

import club.muimi.backend.common.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "用户状态不能为空")
        UserStatus status
) {
}
