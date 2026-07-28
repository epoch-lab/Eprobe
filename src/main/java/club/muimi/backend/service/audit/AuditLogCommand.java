package club.muimi.backend.service.audit;

import club.muimi.backend.common.enums.AuditModule;
import club.muimi.backend.common.enums.AuditSeverity;
import club.muimi.backend.common.enums.Role;
import club.muimi.backend.security.auth.LoginUser;

public record AuditLogCommand(
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
        Object detail
) {

    public static Builder builder(AuditModule module, String action, AuditSeverity severity, String summary) {
        return new Builder(module, action, severity, summary);
    }

    public static final class Builder {
        private final AuditModule module;
        private final String action;
        private final AuditSeverity severity;
        private final String summary;
        private Long actorUserId;
        private String actorUsername;
        private Role actorRole;
        private String targetType;
        private Long targetId;
        private boolean success = true;
        private Object detail;

        private Builder(AuditModule module, String action, AuditSeverity severity, String summary) {
            this.module = module;
            this.action = action;
            this.severity = severity;
            this.summary = summary;
        }

        public Builder actor(LoginUser actor) {
            if (actor != null) {
                this.actorUserId = actor.getUserId();
                this.actorUsername = actor.getDisplayUsername();
                this.actorRole = actor.getRole();
            }
            return this;
        }

        public Builder actor(Long actorUserId, String actorUsername, Role actorRole) {
            this.actorUserId = actorUserId;
            this.actorUsername = actorUsername;
            this.actorRole = actorRole;
            return this;
        }

        public Builder target(String targetType, Long targetId) {
            this.targetType = targetType;
            this.targetId = targetId;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder detail(Object detail) {
            this.detail = detail;
            return this;
        }

        public AuditLogCommand build() {
            return new AuditLogCommand(
                    module,
                    action,
                    severity,
                    actorUserId,
                    actorUsername,
                    actorRole,
                    targetType,
                    targetId,
                    success,
                    summary,
                    detail
            );
        }
    }
}
