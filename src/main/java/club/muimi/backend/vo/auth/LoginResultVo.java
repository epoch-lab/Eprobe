package club.muimi.backend.vo.auth;

import club.muimi.backend.common.enums.Role;
import club.muimi.backend.common.enums.UserStatus;

public record LoginResultVo(
        Long id,
        String username,
        String email,
        Role role,
        UserStatus status,
        boolean emailVerified
) {
}
