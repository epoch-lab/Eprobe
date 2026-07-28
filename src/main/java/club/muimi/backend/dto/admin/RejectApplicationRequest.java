package club.muimi.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectApplicationRequest(
        @NotBlank(message = "拒绝备注不能为空")
        @Size(max = 255, message = "拒绝备注长度不能超过 255 个字符")
        String remark
) {
}
