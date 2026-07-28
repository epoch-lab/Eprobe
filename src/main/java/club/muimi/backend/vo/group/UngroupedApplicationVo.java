package club.muimi.backend.vo.group;

import club.muimi.backend.common.enums.ApplicationStatus;
import club.muimi.backend.common.enums.Grade;

import java.time.OffsetDateTime;

public record UngroupedApplicationVo(
        Long id,
        Long userId,
        String username,
        String email,
        String realName,
        String phone,
        String college,
        String major,
        String className,
        Grade grade,
        Integer admissionYear,
        Long directionLevel1Id,
        String directionLevel1Name,
        Long directionLevel2Id,
        String directionLevel2Name,
        String introduction,
        ApplicationStatus status,
        String statusRemark,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
