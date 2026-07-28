package club.muimi.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "旧密码不能为空")
        String oldPassword,
        @NotBlank(message = "新密码不能为空")
        String newPassword,
        @NotBlank(message = "确认密码不能为空")
        String confirmPassword
) {
}
