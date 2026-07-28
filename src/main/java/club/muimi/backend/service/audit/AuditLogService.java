package club.muimi.backend.service.audit;

import club.muimi.backend.common.api.PageResult;
import club.muimi.backend.common.api.RequestIdFilter;
import club.muimi.backend.common.enums.AuditModule;
import club.muimi.backend.common.enums.AuditSeverity;
import club.muimi.backend.entity.AuditLog;
import club.muimi.backend.repository.AuditLogRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.vo.audit.AuditLogVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditLogService {

    private static final Logger MAJOR_AUDIT_LOGGER = LoggerFactory.getLogger("MAJOR_AUDIT_EVENT");

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;
    private final Clock appClock;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            ObjectMapper objectMapper,
            CurrentUserService currentUserService,
            Clock appClock
    ) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.currentUserService = currentUserService;
        this.appClock = appClock;
    }

    @Transactional
    public void record(AuditLogCommand command) {
        persist(command);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInNewTransaction(AuditLogCommand command) {
        persist(command);
    }

    private void persist(AuditLogCommand command) {
        LoginUser currentUser = currentUserService.getCurrentUser().orElse(null);
        RequestMetadata requestMetadata = resolveRequestMetadata();
        AuditLog auditLog = auditLogRepository.save(AuditLog.builder()
                .module(command.module())
                .action(command.action())
                .severity(command.severity())
                .actorUserId(command.actorUserId() != null ? command.actorUserId() : currentUser == null ? null : currentUser.getUserId())
                .actorUsername(command.actorUsername() != null ? command.actorUsername() : currentUser == null ? null : currentUser.getDisplayUsername())
                .actorRole(command.actorRole() != null ? command.actorRole() : currentUser == null ? null : currentUser.getRole())
                .targetType(command.targetType())
                .targetId(command.targetId())
                .success(command.success())
                .summary(command.summary())
                .detailJson(toDetailJson(command.detail()))
                .requestId(requestMetadata.requestId())
                .requestPath(requestMetadata.requestPath())
                .clientIp(requestMetadata.clientIp())
                .build());
        if (command.severity() == AuditSeverity.MAJOR) {
            MAJOR_AUDIT_LOGGER.info(toMajorEventLine(auditLog));
        }
    }

    @Transactional(readOnly = true)
    public PageResult<AuditLogVo> search(
            int page,
            int size,
            AuditModule module,
            AuditSeverity severity,
            Boolean success,
            Long actorUserId,
            String keyword
    ) {
        return PageResult.from(
                auditLogRepository.search(
                        module,
                        severity,
                        success,
                        actorUserId,
                        normalizeKeyword(keyword),
                        PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"))
                ).map(this::toVo)
        );
    }

    private AuditLogVo toVo(AuditLog auditLog) {
        return new AuditLogVo(
                auditLog.getId(),
                auditLog.getModule(),
                auditLog.getAction(),
                auditLog.getSeverity(),
                auditLog.getActorUserId(),
                auditLog.getActorUsername(),
                auditLog.getActorRole(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                Boolean.TRUE.equals(auditLog.getSuccess()),
                auditLog.getSummary(),
                auditLog.getDetailJson(),
                auditLog.getRequestId(),
                auditLog.getRequestPath(),
                auditLog.getClientIp(),
                auditLog.getCreatedAt().atZone(appClock.getZone()).toOffsetDateTime()
        );
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toDetailJson(Object detail) {
        if (detail == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            return "{\"fallback\":" + quoteForJson(String.valueOf(detail)) + "}";
        }
    }

    private String toMajorEventLine(AuditLog auditLog) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("auditId", auditLog.getId());
        detail.put("module", auditLog.getModule());
        detail.put("action", auditLog.getAction());
        detail.put("summary", auditLog.getSummary());
        detail.put("actorUserId", auditLog.getActorUserId());
        detail.put("actorUsername", auditLog.getActorUsername());
        detail.put("targetType", auditLog.getTargetType());
        detail.put("targetId", auditLog.getTargetId());
        detail.put("success", auditLog.getSuccess());
        detail.put("requestId", auditLog.getRequestId());
        detail.put("requestPath", auditLog.getRequestPath());
        detail.put("clientIp", auditLog.getClientIp());
        detail.put("createdAt", auditLog.getCreatedAt());
        return toDetailJson(detail);
    }

    private String quoteForJson(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "\"serialization-error\"";
        }
    }

    private RequestMetadata resolveRequestMetadata() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return new RequestMetadata("N/A", "N/A", "N/A");
        }
        HttpServletRequest request = attributes.getRequest();
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return new RequestMetadata(
                requestId == null ? "N/A" : String.valueOf(requestId),
                request.getRequestURI(),
                request.getRemoteAddr()
        );
    }

    private record RequestMetadata(String requestId, String requestPath, String clientIp) {
    }
}
