package club.muimi.backend.security.jwt;

import java.time.Instant;

public record JwtClaims(
        Long userId,
        String role,
        Long tokenVersion,
        String jti,
        Instant expiresAt
) {
}
