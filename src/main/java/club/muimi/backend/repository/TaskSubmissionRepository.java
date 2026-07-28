package club.muimi.backend.repository;

import club.muimi.backend.entity.TaskSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, Long> {

    Optional<TaskSubmission> findByTaskIdAndUserId(Long taskId, Long userId);

    @Query(value = "select * from task_submission where task_id = :taskId and user_id = :userId for update", nativeQuery = true)
    Optional<TaskSubmission> findByTaskIdAndUserIdForUpdate(Long taskId, Long userId);

    List<TaskSubmission> findAllByTaskIdIn(Collection<Long> taskIds);

    List<TaskSubmission> findAllByTaskId(Long taskId);
}
