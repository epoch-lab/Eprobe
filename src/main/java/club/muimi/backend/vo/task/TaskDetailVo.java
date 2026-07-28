package club.muimi.backend.vo.task;

import java.time.OffsetDateTime;

public record TaskDetailVo(
        Long id,
        Long groupId,
        String groupName,
        String title,
        String contentMarkdown,
        TaskAttachmentVo attachment,
        Integer maxScore,
        OffsetDateTime deadlineAt,
        Long publisherUserId,
        String publisherUsername,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        TaskSubmissionVo currentUserSubmission
) {
}
