package club.muimi.backend.repository;

import club.muimi.backend.entity.LearningMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LearningMaterialRepository extends JpaRepository<LearningMaterial, Long> {

    List<LearningMaterial> findAllByGroupIdOrderByCreatedAtDesc(Long groupId);

    List<LearningMaterial> findAllByGroupIdInOrderByCreatedAtDesc(Collection<Long> groupIds);
}
