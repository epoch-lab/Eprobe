package club.muimi.backend.entity;

import club.muimi.backend.common.enums.StoredFilePurpose;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FileUploadSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(64)")
    private StoredFilePurpose purpose;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "total_size", nullable = false)
    private long totalSize;

    @Column(name = "chunk_size", nullable = false)
    private long chunkSize;

    @Column(name = "received_size", nullable = false)
    @Builder.Default
    private long receivedSize = 0;

    @Column(name = "next_chunk_index", nullable = false)
    @Builder.Default
    private int nextChunkIndex = 0;

    @Column(name = "temp_storage_path", nullable = false, length = 512)
    private String tempStoragePath;

    @Column(name = "uploader_user_id", nullable = false)
    private Long uploaderUserId;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
