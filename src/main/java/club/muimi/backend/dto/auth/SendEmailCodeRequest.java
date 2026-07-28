package club.muimi.backend.dto.auth;

import club.muimi.backend.common.enums.EmailCodeScene;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendEmailCodeRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,
        @NotNull(message = "验证码场景不能为空")
        EmailCodeScene scene
) {
}
