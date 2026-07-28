package club.muimi.backend.dto.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertLearningMaterialRequest(
        @NotBlank(message = "资料标题不能为空")
        @Size(max = 100, message = "资料标题长度不能超过 100 个字符")
        String title,
        @Size(max = 20000, message = "资料内容长度不能超过 20000 个字符")
        String contentMarkdown,
        Long attachmentFileId,
        boolean removeAttachment
) {
}
