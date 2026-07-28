package club.muimi.backend.repository;

import club.muimi.backend.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findAllByOrderByCreatedAtDesc();

    List<Announcement> findAllByScopeAndGroupIdInOrScopeOrderByCreatedAtDesc(
            club.muimi.backend.common.enums.AnnouncementScope groupScope,
            Collection<Long> groupIds,
            club.muimi.backend.common.enums.AnnouncementScope globalScope
    );
}
