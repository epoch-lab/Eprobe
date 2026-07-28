package club.muimi.backend.repository;

import club.muimi.backend.common.enums.AuditModule;
import club.muimi.backend.common.enums.AuditSeverity;
import club.muimi.backend.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a
            from AuditLog a
            where (:module is null or a.module = :module)
              and (:severity is null or a.severity = :severity)
              and (:success is null or a.success = :success)
              and (:actorUserId is null or a.actorUserId = :actorUserId)
              and (:keyword is null
                   or lower(a.summary) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(a.actorUsername, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(a.targetType, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<AuditLog> search(
            @Param("module") AuditModule module,
            @Param("severity") AuditSeverity severity,
            @Param("success") Boolean success,
            @Param("actorUserId") Long actorUserId,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
