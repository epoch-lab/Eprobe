package club.muimi.backend.repository;

import club.muimi.backend.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findAllByUserId(Long userId);

    List<GroupMember> findAllByUserIdIn(Collection<Long> userIds);

    List<GroupMember> findAllByGroupId(Long groupId);

    List<GroupMember> findAllByGroupIdIn(Collection<Long> groupIds);

    boolean existsByUserIdAndGroupId(Long userId, Long groupId);

    Optional<GroupMember> findByApplicationId(Long applicationId);

    Optional<GroupMember> findByGroupIdAndApplicationId(Long groupId, Long applicationId);

    List<GroupMember> findAllByApplicationIdIn(Collection<Long> applicationIds);

    long countByGroupId(Long groupId);

    @Query(value = "select count(*) from group_member where group_id = :groupId for update", nativeQuery = true)
    long countByGroupIdForUpdate(Long groupId);

    long countByUserId(Long userId);
}
