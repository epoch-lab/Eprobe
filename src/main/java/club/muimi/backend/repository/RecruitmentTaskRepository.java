package club.muimi.backend.repository;

import club.muimi.backend.entity.RecruitmentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecruitmentTaskRepository extends JpaRepository<RecruitmentTask, Long> {

    List<RecruitmentTask> findAllByGroupIdOrderByCreatedAtDesc(Long groupId);

    List<RecruitmentTask> findAllByGroupIdInOrderByCreatedAtDesc(Collection<Long> groupIds);

    List<RecruitmentTask> findAllByIdIn(Collection<Long> ids);

    @Query(value = "select * from recruitment_task where id = :id for update", nativeQuery = true)
    Optional<RecruitmentTask> findByIdForUpdate(Long id);
}
