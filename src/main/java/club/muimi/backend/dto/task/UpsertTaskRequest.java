package club.muimi.backend.dto.task;

import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;

public record UpsertTaskRequest(
        @NotBlank(message = "任务标题不能为空")
        @Size(max = 100, message = "任务标题长度不能超过 100 个字符")
        String title,
        @Size(max = 20000, message = "任务内容长度不能超过 20000 个字符")
        String contentMarkdown,
        Long attachmentFileId,
        boolean removeAttachment,
        @NotNull(message = "任务满分不能为空")
        @Min(value = 1, message = "任务满分必须大于等于 1")
        @Max(value = 100, message = "任务满分必须小于等于 100")
        Integer maxScore,
        @NotNull(message = "任务截止时间不能为空")
        OffsetDateTime deadlineAt
) {
}
