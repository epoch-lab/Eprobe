package club.muimi.backend.dto.task;

import jakarta.validation.constraints.Size;

public record SubmitTaskRequest(
        @Size(max = 20000, message = "提交内容长度不能超过 20000 个字符")
        String contentMarkdown,
        Long attachmentFileId
) {
}
