package club.muimi.backend.dto.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewTaskSubmissionRequest(
        @NotNull(message = "评分不能为空")
        @Min(value = 0, message = "评分不能小于 0")
        @Max(value = 100, message = "评分不能大于 100")
        Integer score,
        @Size(max = 5000, message = "评语长度不能超过 5000 个字符")
        String comment
) {
}
