package club.muimi.backend.dto.admin;

import club.muimi.backend.common.enums.Grade;
import jakarta.validation.constraints.*;

public record GroupUpsertRequest(
        @NotBlank(message = "分组名称不能为空")
        @Size(max = 128, message = "分组名称长度不能超过 128 个字符")
        String name,
        @NotNull(message = "一级方向不能为空")
        Long directionLevel1Id,
        @NotNull(message = "二级方向不能为空")
        Long directionLevel2Id,
        @NotNull(message = "年级不能为空")
        Grade grade,
        @NotNull(message = "入学年份不能为空")
        @Min(value = 1000, message = "入学年份必须为四位数")
        @Max(value = 9999, message = "入学年份必须为四位数")
        Integer admissionYear,
        @NotNull(message = "分组人数上限不能为空")
        @Positive(message = "分组人数上限必须大于 0")
        Integer maxSize
) {
}
