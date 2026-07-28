package club.muimi.backend.vo.admin;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;
import club.muimi.backend.vo.auth.GroupSimpleVo;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserDetailVo(
        Long id,
        String username,
        String email,
        Role role,
        UserStatus status,
        boolean emailVerified,
        List<GroupSimpleVo> leaderGroups,
        long applicationCount,
        long groupCount,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<GroupSimpleVo> groups
) {
}
