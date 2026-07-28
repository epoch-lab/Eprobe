package club.muimi.backend.dto.admin;

import jakarta.validation.constraints.Size;

public record UnassignGroupApplicationRequest(
        @Size(max = 255, message = "状态备注长度不能超过 255 个字符")
        String remark
) {
}
