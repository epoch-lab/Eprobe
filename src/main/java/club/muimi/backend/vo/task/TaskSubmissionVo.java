package club.muimi.backend.vo.task;

import club.muimi.backend.common.enums.TaskSubmissionStatus;

import java.time.OffsetDateTime;

public record TaskSubmissionVo(
        Long taskId,
        Long userId,
        TaskSubmissionStatus status,
        String contentMarkdown,
        TaskAttachmentVo attachment,
        OffsetDateTime submittedAt,
        Long reviewerUserId,
        String reviewerUsername,
        Integer score,
        String reviewComment,
        OffsetDateTime reviewedAt
) {
}
