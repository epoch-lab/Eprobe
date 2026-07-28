package club.muimi.backend.vo.task;

import club.muimi.backend.common.enums.TaskSubmissionStatus;

import java.time.OffsetDateTime;

public record TaskMemberSubmissionVo(
        Long userId,
        String username,
        String realName,
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
