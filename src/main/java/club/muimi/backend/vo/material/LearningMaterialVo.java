package club.muimi.backend.vo.material;

import club.muimi.backend.vo.task.TaskAttachmentVo;

import java.time.OffsetDateTime;

public record LearningMaterialVo(
        Long id,
        Long groupId,
        String groupName,
        String title,
        String contentMarkdown,
        TaskAttachmentVo attachment,
        Long publisherUserId,
        String publisherUsername,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
