package club.muimi.backend.repository;

import club.muimi.backend.entity.FileUploadSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FileUploadSessionRepository extends JpaRepository<FileUploadSession, Long> {

    Optional<FileUploadSession> findByIdAndUploaderUserId(Long id, Long uploaderUserId);

    List<FileUploadSession> findAllByUpdatedAtBefore(LocalDateTime updatedAt);
}
