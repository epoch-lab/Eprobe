package club.muimi.backend.repository;

import club.muimi.backend.common.enums.Grade;
import club.muimi.backend.entity.RecruitmentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecruitmentGroupRepository extends JpaRepository<RecruitmentGroup, Long> {

    List<RecruitmentGroup> findAllByOrderByCreatedAtDesc();

    List<RecruitmentGroup> findAllByIdIn(Collection<Long> ids);

    List<RecruitmentGroup> findAllByLeaderUserIdOrderByCreatedAtDesc(Long leaderUserId);

    List<RecruitmentGroup> findAllByLeaderUserId(Long leaderUserId);

    List<RecruitmentGroup> findAllByLeaderUserIdIn(Collection<Long> leaderUserIds);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query(value = "select * from recruitment_group where id = :id for update", nativeQuery = true)
    Optional<RecruitmentGroup> findByIdForUpdate(Long id);

    boolean existsByIdAndLeaderUserId(Long id, Long leaderUserId);

    boolean existsByDirectionLevel1Id(Long directionLevel1Id);

    boolean existsByDirectionLevel2Id(Long directionLevel2Id);

    boolean existsByLeaderUserIdAndDirectionLevel1IdAndDirectionLevel2IdAndGradeAndAdmissionYear(
            Long leaderUserId,
            Long directionLevel1Id,
            Long directionLevel2Id,
            Grade grade,
            Integer admissionYear
    );
}
