package club.muimi.backend.vo.group;

import club.muimi.backend.common.enums.ApplicationStatus;
import club.muimi.backend.common.enums.Grade;

public record GroupMemberVo(
        Long userId,
        String username,
        String realName,
        Long applicationId,
        Grade grade,
        Integer admissionYear,
        String directionLevel1Name,
        String directionLevel2Name,
        ApplicationStatus applicationStatus
) {
}
