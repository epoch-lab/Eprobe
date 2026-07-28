package club.muimi.backend.vo.audit;

import club.muimi.backend.common.enums.AuditModule;
import club.muimi.backend.common.enums.AuditSeverity;
import club.muimi.backend.common.enums.Role;

import java.time.OffsetDateTime;

public record AuditLogVo(
        Long id,
        AuditModule module,
        String action,
        AuditSeverity severity,
        Long actorUserId,
        String actorUsername,
        Role actorRole,
        String targetType,
        Long targetId,
        boolean success,
        String summary,
        String detailJson,
        String requestId,
        String requestPath,
        String clientIp,
        OffsetDateTime createdAt
) {
}
