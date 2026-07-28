package club.muimi.backend.repository;

import club.muimi.backend.entity.Direction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DirectionRepository extends JpaRepository<Direction, Long> {

    List<Direction> findAllByOrderByLevelAscSortOrderAscIdAsc();

    List<Direction> findAllByEnabledTrueOrderByLevelAscSortOrderAscIdAsc();

    List<Direction> findAllByParentId(Long parentId);

    boolean existsByParentId(Long parentId);

    boolean existsByParentIdAndNameIgnoreCase(Long parentId, String name);

    boolean existsByParentIdAndNameIgnoreCaseAndIdNot(Long parentId, String name, Long id);
}
