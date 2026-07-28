package club.muimi.backend.vo.task;

import java.time.OffsetDateTime;

public record ManageTaskVo(
        Long id,
        Long groupId,
        String groupName,
        String title,
        Integer maxScore,
        OffsetDateTime deadlineAt,
        long memberCount,
        long pendingCount,
        long submittedCount,
        long reviewedCount,
        double completionRate,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
