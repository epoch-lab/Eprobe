package club.muimi.backend.repository;

import club.muimi.backend.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByIdAndUploaderUserId(Long id, Long uploaderUserId);

    List<StoredFile> findAllByIdIn(Collection<Long> ids);

    List<StoredFile> findAllByBindingTypeIsNullAndBindingIdIsNullAndCreatedAtBefore(LocalDateTime createdAt);
}
