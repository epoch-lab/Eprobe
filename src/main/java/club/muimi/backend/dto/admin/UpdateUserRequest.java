package club.muimi.backend.dto.admin;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import jakarta.validation.constraints.*;

public record UpdateUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度必须在 3 到 32 位之间")
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
        String username,
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,
        @Size(min = 8, message = "密码至少 8 位")
        String password,
        String confirmPassword,
        @NotNull(message = "用户角色不能为空")
        Role role,
        @NotNull(message = "用户状态不能为空")
        UserStatus status,
        Boolean emailVerified
) {
    public UpdateUserRequest(String username, String email, String password,
                             Role role, UserStatus status, Boolean emailVerified) {
        this(username, email, password, password, role, status, emailVerified);
    }
}
