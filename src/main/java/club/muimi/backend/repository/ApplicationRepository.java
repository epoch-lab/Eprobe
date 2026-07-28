package club.muimi.backend.repository;

import club.muimi.backend.common.enums.ApplicationStatus;
import club.muimi.backend.entity.Application;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<Application> findAllByUserIdIn(Collection<Long> userIds);

    Optional<Application> findByIdAndUserId(Long id, Long userId);

    @Query(value = "select * from `application` where id = :id for update", nativeQuery = true)
    Optional<Application> findByIdForUpdate(Long id);

    boolean existsByUserIdAndDirectionLevel2Id(Long userId, Long directionLevel2Id);

    boolean existsByUserIdAndDirectionLevel2IdAndIdNot(Long userId, Long directionLevel2Id, Long id);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, ApplicationStatus status);

    List<Application> findAllByStatus(ApplicationStatus status, Sort sort);

    @Query("""
            select a
            from Application a
            where a.status = club.muimi.backend.common.enums.ApplicationStatus.SUBMITTED
              and not exists (
                  select 1
                  from GroupMember gm
                  where gm.applicationId = a.id
              )
            """)
    List<Application> findAllUngroupedSubmittedApplications(Sort sort);

    boolean existsByDirectionLevel1Id(Long directionLevel1Id);

    boolean existsByDirectionLevel2Id(Long directionLevel2Id);
}
