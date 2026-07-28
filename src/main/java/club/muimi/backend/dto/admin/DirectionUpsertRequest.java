package club.muimi.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DirectionUpsertRequest(
        Long parentId,
        @NotBlank(message = "方向名称不能为空")
        @Size(max = 64, message = "方向名称长度不能超过 64 个字符")
        String name,
        @NotNull(message = "排序值不能为空")
        @PositiveOrZero(message = "排序值不能小于 0")
        Integer sortOrder,
        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}
