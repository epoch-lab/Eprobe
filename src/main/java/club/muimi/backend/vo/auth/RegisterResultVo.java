package club.muimi.backend.vo.auth;

import club.muimi.backend.common.enums.Role;

public record RegisterResultVo(
        Long id,
        String username,
        String email,
        Role role
) {
}
