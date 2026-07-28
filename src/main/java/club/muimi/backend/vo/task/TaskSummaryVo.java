package club.muimi.backend.vo.task;

import club.muimi.backend.common.enums.TaskSubmissionStatus;

import java.time.OffsetDateTime;

public record TaskSummaryVo(
        Long id,
        Long groupId,
        String groupName,
        String title,
        Integer maxScore,
        OffsetDateTime deadlineAt,
        TaskSubmissionStatus submissionStatus,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewedAt
) {
}
