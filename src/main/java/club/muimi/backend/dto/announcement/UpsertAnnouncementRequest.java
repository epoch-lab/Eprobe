package club.muimi.backend.dto.announcement;

import club.muimi.backend.common.enums.AnnouncementScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertAnnouncementRequest(
        @NotBlank(message = "公告标题不能为空")
        @Size(max = 100, message = "公告标题长度不能超过 100 个字符")
        String title,
        @NotBlank(message = "公告内容不能为空")
        @Size(max = 20000, message = "公告内容长度不能超过 20000 个字符")
        String contentMarkdown,
        @NotNull(message = "公告范围不能为空")
        AnnouncementScope scope,
        Long groupId
) {
}
