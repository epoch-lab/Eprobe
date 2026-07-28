package club.muimi.backend.vo.group;

import club.muimi.backend.common.enums.Grade;

import java.time.OffsetDateTime;

public record GroupDetailVo(
        Long id,
        String name,
        Long directionLevel1Id,
        String directionLevel1Name,
        Long directionLevel2Id,
        String directionLevel2Name,
        Grade grade,
        Integer admissionYear,
        Integer maxSize,
        long currentSize,
        Long leaderUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
