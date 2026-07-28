package club.muimi.backend.vo.auth;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;

import java.util.List;

public record CurrentUserVo(
        Long id,
        String username,
        String email,
        Role role,
        UserStatus status,
        boolean emailVerified,
        List<GroupSimpleVo> leaderGroups,
        List<GroupSimpleVo> groups
) {
}
