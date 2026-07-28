package club.muimi.backend.dto.application;

import club.muimi.backend.common.enums.Grade;
import jakarta.validation.constraints.*;

public record UpsertApplicationRequest(
        @NotBlank(message = "真实姓名不能为空")
        @Size(min = 1, max = 32, message = "真实姓名长度必须在 1 到 32 个字符之间")
        String realName,
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
        String phone,
        @NotBlank(message = "学院不能为空")
        @Size(min = 1, max = 64, message = "学院长度必须在 1 到 64 个字符之间")
        String college,
        @NotBlank(message = "专业不能为空")
        @Size(min = 1, max = 64, message = "专业长度必须在 1 到 64 个字符之间")
        String major,
        @NotBlank(message = "班级不能为空")
        @Size(min = 1, max = 64, message = "班级长度必须在 1 到 64 个字符之间")
        String className,
        @NotNull(message = "年级不能为空")
        Grade grade,
        @NotNull(message = "入学年份不能为空")
        @Min(value = 1000, message = "入学年份必须为四位数")
        @Max(value = 9999, message = "入学年份必须为四位数")
        Integer admissionYear,
        @NotNull(message = "一级方向不能为空")
        Long directionLevel1Id,
        @NotNull(message = "二级方向不能为空")
        Long directionLevel2Id,
        @Size(max = 1000, message = "自我介绍长度不能超过 1000 个字符")
        String introduction
) {
}
