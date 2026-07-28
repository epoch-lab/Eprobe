package club.muimi.backend.vo.file;

public record UploadSessionVo(
        Long sessionId,
        long chunkSizeBytes,
        long totalSize,
        int nextChunkIndex
) {
}
