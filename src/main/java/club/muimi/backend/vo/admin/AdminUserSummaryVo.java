package club.muimi.backend.vo.admin;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;

import java.time.LocalDateTime;

public record AdminUserSummaryVo(
        Long id,
        String username,
        String email,
        Role role,
        UserStatus status,
        boolean emailVerified,
        long leaderGroupCount,
        long applicationCount,
        long groupCount,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}
